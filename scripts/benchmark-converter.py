#!/usr/bin/env python3
"""Streaming smoke profile for the installed Geometry Converter.

This is not a hard performance gate. It records wall time and peak child RSS for a bounded
100k-point stream and verifies that every row reached the converter.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import platform
import resource
import subprocess
import tempfile
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLUGIN_ROOT = "plugins/transforms/hop-geoprocessing"
GEOMETRY_ROOT = "plugins/misc/hop-geometry-type"


def extract(zip_path: Path, hop_home: Path, root: str) -> None:
    if not zip_path.is_file():
        raise SystemExit(f"Missing ZIP: {zip_path}")
    with zipfile.ZipFile(zip_path) as archive:
        if archive.testzip() is not None:
            raise SystemExit(f"Corrupt ZIP: {zip_path}")
        if not any(name.startswith(root + "/") for name in archive.namelist()):
            raise SystemExit(f"{zip_path} lacks installation root {root}")
        archive.extractall(hop_home)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--hop-home", required=True, type=Path)
    parser.add_argument("--plugin-zip", required=True, type=Path)
    parser.add_argument("--geometry-zip", required=True, type=Path)
    parser.add_argument("--rows", type=int, default=100_000)
    parser.add_argument("--heap", default="128m")
    args = parser.parse_args()

    hop_home = args.hop_home.resolve()
    if not (hop_home / "hop-run.sh").is_file():
        raise SystemExit(f"Not an Apache Hop home: {hop_home}")
    if (hop_home / PLUGIN_ROOT).exists() or (hop_home / GEOMETRY_ROOT).exists():
        raise SystemExit("Benchmark Hop home must not contain the plugins yet")

    with tempfile.TemporaryDirectory(prefix="hop-converter-benchmark-") as temporary:
        work = Path(temporary)
        input_dir = work / "input"
        output_dir = work / "output"
        config = work / "config"
        audit = work / "audit"
        input_dir.mkdir()
        output_dir.mkdir()
        audit.mkdir()
        (config / "metadata/pipeline-run-configuration").mkdir(parents=True)
        (config / "metadata/pipeline-run-configuration/local.json").write_text(
            '{"name":"local","engineRunConfiguration":{"Local":'
            '{"rowset_size":"2","safe_mode":false}},"configurationVariables":[]}\n',
            encoding="utf-8",
        )
        with (input_dir / "benchmark.csv").open("w", newline="", encoding="utf-8") as stream:
            writer = csv.writer(stream, delimiter=";", lineterminator="\n")
            writer.writerow(["id", "wkt"])
            for i in range(1, args.rows + 1):
                writer.writerow([i, f"POINT ({i} {i})"])

        extract(args.geometry_zip.resolve(), hop_home, GEOMETRY_ROOT)
        extract(args.plugin_zip.resolve(), hop_home, PLUGIN_ROOT)

        env = os.environ.copy()
        env["HOP_CONFIG_FOLDER"] = str(config)
        env["HOP_AUDIT_FOLDER"] = str(audit)
        env["HOP_OPTIONS"] = f"-Xmx{args.heap}"
        if env.get("JAVA_HOME"):
            env["HOP_JAVA_HOME"] = env["JAVA_HOME"]
        command = [
            str(hop_home / "hop-run.sh"),
            "-r",
            "local",
            "-f",
            str(ROOT / "e2e/geometry-converter/streaming-benchmark.hpl"),
            "-p",
            f"E2E_INPUT_DIR={input_dir}",
            "-p",
            f"E2E_OUTPUT_DIR={output_dir}",
        ]
        started = time.perf_counter()
        result = subprocess.run(
            command, env=env, text=True, capture_output=True, timeout=900, check=False
        )
        elapsed = time.perf_counter() - started
        if result.returncode != 0:
            raise SystemExit(
                f"Benchmark pipeline failed ({result.returncode}).\n"
                + result.stdout
                + "\n"
                + result.stderr
            )
        expected = f"Geometry Converter summary: input={args.rows}, normal={args.rows}, errors=0"
        if expected not in result.stdout:
            raise SystemExit(
                f"Benchmark did not process exactly {args.rows} rows.\n" + result.stdout
            )
        max_rss = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
        if platform.system() == "Darwin":
            max_rss_mb = max_rss / (1024 * 1024)
        else:
            max_rss_mb = max_rss / 1024
        report = {
            "rows": args.rows,
            "wall_seconds": round(elapsed, 3),
            "max_child_rss_mb": round(max_rss_mb, 1),
            "heap": f"-Xmx{args.heap}",
            "java": os.environ.get("JAVA_HOME", "unknown"),
            "os": platform.platform(),
            "pipeline": "e2e/geometry-converter/streaming-benchmark.hpl",
            "plugin_zip_sha256": __import__("hashlib").sha256(
                args.plugin_zip.read_bytes()
            ).hexdigest(),
            "geometry_zip_sha256": __import__("hashlib").sha256(
                args.geometry_zip.read_bytes()
            ).hexdigest(),
        }
        print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
