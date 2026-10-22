#!/usr/bin/env python3
"""Run the packaged Geoprocessing suite against an isolated Hop installation."""

from __future__ import annotations

import argparse
import csv
import io
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import struct
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
PLUGIN_ROOT = "plugins/transforms/hop-geoprocessing"
GEOMETRY_ROOT = "plugins/misc/hop-geometry-type"


def extract_plugin(zip_path: Path, hop_home: Path, expected_root: str) -> None:
    if not zip_path.is_file():
        raise SystemExit(f"Missing plugin ZIP: {zip_path}")
    with zipfile.ZipFile(zip_path) as archive:
        if archive.testzip() is not None:
            raise SystemExit(f"Corrupt plugin ZIP: {zip_path}")
        entries = archive.namelist()
        prefix = expected_root.rstrip("/") + "/"
        if not any(entry.startswith(prefix) for entry in entries):
            raise SystemExit(f"{zip_path.name} lacks installation root {prefix}")
        for entry in entries:
            path = Path(entry)
            if path.is_absolute() or ".." in path.parts:
                raise SystemExit(f"Unsafe ZIP entry {entry!r} in {zip_path.name}")
        archive.extractall(hop_home)


def run_pipeline(
    hop_home: Path, pipeline: Path, env: dict[str, str], input_dir: Path, output_dir: Path
) -> None:
    command = [
        str(hop_home / "hop-run.sh"),
        "-r",
        "local",
        "-f",
        str(pipeline),
        "-p",
        f"E2E_INPUT_DIR={input_dir}",
        "-p",
        f"E2E_OUTPUT_DIR={output_dir}",
    ]
    print("==>", " ".join(command), flush=True)
    subprocess.run(command, check=True, env=env, timeout=300)


def check_output(output_file: Path) -> None:
    if not output_file.is_file():
        raise SystemExit(f"Hop did not create the expected output: {output_file}")
    with output_file.open(newline="", encoding="utf-8") as stream:
        rows = list(csv.reader(stream, delimiter=";"))
    if rows != [
        ["geometry", "centroid"],
        ["POINT (3 4)", "POINT (3 4)"],
        ["POINT (7 8)", "POINT (7 8)"],
    ]:
        raise SystemExit(f"Unexpected Geoprocessing output: {rows!r}")



def write_input(path: Path, header: list[str], rows: list[list[str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, delimiter=";", lineterminator="\n")
        writer.writerow(header)
        writer.writerows(rows)


def read_output(path: Path) -> list[list[str]]:
    if not path.is_file():
        raise SystemExit(f"Hop did not create the expected output: {path}")
    with path.open(newline="", encoding="utf-8") as stream:
        return list(csv.reader(stream, delimiter=";"))


def require_output(path: Path, expected: list[list[str]]) -> None:
    actual = read_output(path)
    if actual != expected:
        raise SystemExit(
            f"Unexpected converter output in {path}.\n"
            f"--- actual ---\n{actual!r}\n--- expected ---\n{expected!r}"
        )


def run_converter_pipeline(
    hop_home: Path,
    pipeline_name: str,
    env: dict[str, str],
    input_dir: Path,
    output_dir: Path,
    parameters: dict[str, str] | None = None,
) -> subprocess.CompletedProcess[str]:
    command = [
        str(hop_home / "hop-run.sh"),
        "-r",
        "local",
        "-f",
        str(ROOT / "e2e/geometry-converter" / pipeline_name),
        "-p",
        f"E2E_INPUT_DIR={input_dir}",
        "-p",
        f"E2E_OUTPUT_DIR={output_dir}",
    ]
    for name, value in (parameters or {}).items():
        command.extend(["-p", f"{name}={value}"])
    print("==>", " ".join(command), flush=True)
    result = subprocess.run(
        command, check=False, env=env, text=True, capture_output=True, timeout=300
    )
    if result.returncode != 0:
        raise SystemExit(
            f"Hop failed for {pipeline_name} (exit {result.returncode}).\n"
            f"--- stdout ---\n{result.stdout}\n--- stderr ---\n{result.stderr}"
        )
    return result


def run_converter_e2e(
    hop_home: Path, env: dict[str, str], input_dir: Path, output_dir: Path
) -> None:
    # Coordinate input, SRID assignment and an existing native geometry consumer.
    write_input(
        input_dir / "point-fields.csv",
        ["id", "x", "y", "z", "m"],
        [
            ["1", "1", "2", "", ""],
            ["2", "3", "4", "5", ""],
            ["3", "6", "7", "", "8"],
            ["4", "9", "10", "11", "12"],
        ],
    )
    run_converter_pipeline(hop_home, "point-fields.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "point-fields.csv",
        [
            ["id", "ewkt"],
            ["1", "SRID=2056;POINT ZM (1 2 NaN NaN)"],
            ["2", "SRID=2056;POINT ZM (3 4 5 NaN)"],
            ["3", "SRID=2056;POINT ZM (6 7 NaN 8)"],
            ["4", "SRID=2056;POINT ZM (9 10 11 12)"],
        ],
    )


    # Explicit layout variants: only configured ordinates are part of the EWKT layout.
    write_input(
        input_dir / "point-layouts.csv",
        ["id", "x", "y", "z", "m"],
        [
            ["1", "1", "2", "3", "4"],
            ["2", "5", "6", "7", "8"],
        ],
    )
    run_converter_pipeline(hop_home, "point-layouts.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "point-xy.csv",
        [["id", "xy"], ["1", "SRID=2056;POINT (1 2)"], ["2", "SRID=2056;POINT (5 6)"]],
    )
    require_output(
        output_dir / "point-xyz.csv",
        [["id", "xyz"], ["1", "SRID=2056;POINT Z (1 2 3)"], ["2", "SRID=2056;POINT Z (5 6 7)"]],
    )
    require_output(
        output_dir / "point-xym.csv",
        [["id", "xym"], ["1", "SRID=2056;POINT M (1 2 4)"], ["2", "SRID=2056;POINT M (5 6 8)"]],
    )
    require_output(
        output_dir / "point-xyzm.csv",
        [["id", "xyzm"], ["1", "SRID=2056;POINT ZM (1 2 3 4)"], ["2", "SRID=2056;POINT ZM (5 6 7 8)"]],
    )

    # Real Select Values in both directions.
    write_input(
        input_dir / "select-values-binary.csv",
        ["id", "wkt"],
        [["1", "SRID=2056;POINT ZM (1 2 3 4)"]],
    )
    run_converter_pipeline(hop_home, "select-values-binary.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "select-values-binary.csv",
        [
            ["id", "decoded"],
            ["1", "SRID=2056;POINT ZM (1 2 3 4)"],
        ],
    )

    # Independent ISO WKB BE fixture for POINT Z (1 2 3).
    iso_wkb_hex = struct.pack(">BI3d", 0, 1001, 1.0, 2.0, 3.0).hex().upper()
    write_input(
        input_dir / "encoded-output.csv",
        ["id", "wkt"],
        [["1", "SRID=2056;POINT Z (1 2 3)"]],
    )
    run_converter_pipeline(hop_home, "encoded-output.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "encoded-output.csv",
        [
            ["id", "hex_be", "wkt_norm", "ewkt"],
            ["1", iso_wkb_hex, "POINT Z (1 2 3)", "SRID=2056;POINT Z (1 2 3)"],
        ],
    )

    # True-curve controls and the existing linearize consumer.
    write_input(
        input_dir / "curve-roundtrip.csv",
        ["id", "wkt"],
        [["1", "SRID=2056;CIRCULARSTRING Z (1 0 3, 0 1 3, -1 0 3)"]],
    )
    run_converter_pipeline(hop_home, "curve-roundtrip.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "curve-roundtrip.csv",
        [
            ["id", "roundtrip"],
            ["1", "SRID=2056;CIRCULARSTRING Z (1 0 3, 0 1 3, -1 0 3)"],
        ],
    )

    # Existing field in the middle: overwrite, not append.
    write_input(
        input_dir / "overwrite.csv",
        ["id", "geometry", "tail"],
        [["1", "POINT (1 2)", "keep"]],
    )
    run_converter_pipeline(hop_home, "overwrite.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "overwrite.csv",
        [["id", "geometry", "tail"], ["1", "POINT (1 2)", "keep"]],
    )

    # RETURN_NULL keeps the stream aligned.
    write_input(
        input_dir / "error-null.csv",
        ["id", "wkt"],
        [
            ["1", "SRID=2056;POINT (1 2)"],
            ["2", "not wkt"],
            ["3", "POINT (3 4)"],
            ["4", ""],
        ],
    )
    run_converter_pipeline(hop_home, "error-null.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "error-null.csv",
        [
            ["id", "out"],
            ["1", "POINT (1 2)"],
            ["2", ""],
            ["3", "POINT (3 4)"],
            ["4", ""],
        ],
    )

    # A real error hop: normal and error IDs must partition the input.
    write_input(
        input_dir / "error-hop.csv",
        ["id", "wkt"],
        [
            ["1", "SRID=2056;POINT (1 2)"],
            ["2", "not wkt"],
            ["3", "POINT (3 4)"],
            ["4", "bad"],
            ["5", "POINT (5 6)"],
        ],
    )
    run_converter_pipeline(hop_home, "error-hop.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "error-hop-normal.csv",
        [
            ["id", "out"],
            ["1", "POINT (1 2)"],
            ["3", "POINT (3 4)"],
            ["5", "POINT (5 6)"],
        ],
    )
    error_rows = read_output(output_dir / "error-hop-errors.csv")
    if error_rows[0] != ["id", "wkt", "errors", "error_desc", "error_fields", "error_codes"]:
        raise SystemExit(f"Unexpected error-hop header: {error_rows[0]!r}")
    if [row[0] for row in error_rows[1:]] != ["2", "4"]:
        raise SystemExit(f"Unexpected error-hop IDs: {error_rows[1:]!r}")
    if any("GC_INVALID_WKT" not in row for row in error_rows[1:]):
        raise SystemExit(f"Missing stable error code in error-hop: {error_rows[1:]!r}")

    # Serialization through SortRows spilling; capture the actual temp-file log.
    serialization_rows = [
        [str(i), f"SRID=2056;POINT ({i} {i})"] for i in range(1, 5001)
    ]
    write_input(input_dir / "serialization.csv", ["id", "wkt"], serialization_rows)
    spill = run_converter_pipeline(
        hop_home, "serialization.hpl", env, input_dir, output_dir
    )
    spill_log = spill.stdout + "\n" + spill.stderr
    if "Opening" not in spill_log or "tmp-files" not in spill_log:
        raise SystemExit(
            "serialization.hpl did not prove SortRows spilling (tmp-file log missing).\n"
            + spill_log
        )
    serialized = read_output(output_dir / "serialization.csv")
    expected_serialized = [["id", "ewkt"]] + [
        [str(i), f"SRID=2056;POINT ({i} {i})"] for i in range(1, 5001)
    ]
    if serialized != expected_serialized:
        first_diff = next(
            (i for i, (a, b) in enumerate(zip(serialized, expected_serialized)) if a != b),
            min(len(serialized), len(expected_serialized)),
        )
        raise SystemExit(
            "serialization.hpl did not preserve all 5000 geometry rows. "
            f"lengths actual={len(serialized)} expected={len(expected_serialized)}, "
            f"first_diff={first_diff}: actual={serialized[first_diff:first_diff+3]!r}, "
            f"expected={expected_serialized[first_diff:first_diff+3]!r}"
        )

    # Variable field names are persisted as raw ${...} and resolved by Hop.
    write_input(
        input_dir / "variable-fields.csv",
        ["id", "wkt_in"],
        [["1", "SRID=2056;POINT (1 2)"]],
    )
    run_converter_pipeline(
        hop_home,
        "variable-fields.hpl",
        env,
        input_dir,
        output_dir,
        {"SRC_FIELD": "wkt_in", "DST_FIELD": "wkt_out"},
    )
    require_output(
        output_dir / "variable-fields.csv",
        [["id", "wkt_in", "wkt_out"], ["1", "SRID=2056;POINT (1 2)", "POINT (1 2)"]],
    )

    # Mixed regression rows: EMPTY, curve, valid and invalid, with a real error hop.
    write_input(
        input_dir / "ost-regressions.csv",
        ["id", "wkt"],
        [
            ["1", "SRID=2056;POINT (1 2)"],
            ["2", "not wkt"],
            ["3", "SRID=2056;POINT EMPTY"],
            ["4", "SRID=2056;CIRCULARSTRING (0 0, 1 1, 2 0)"],
            ["5", "SRID=2056;LINESTRING (0 0, 1 1)"],
            ["6", "bad"],
        ],
    )
    run_converter_pipeline(hop_home, "ost-regressions.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "ost-regressions-normal.csv",
        [
            ["id", "out"],
            ["1", "SRID=2056;POINT (1 2)"],
            ["3", "SRID=2056;POINT EMPTY"],
            ["4", "SRID=2056;CIRCULARSTRING (0 0, 1 1, 2 0)"],
            ["5", "SRID=2056;LINESTRING (0 0, 1 1)"],
        ],
    )
    ost_errors = read_output(output_dir / "ost-regressions-errors.csv")
    if [row[0] for row in ost_errors[1:]] != ["2", "6"]:
        raise SystemExit(f"Unexpected OST error IDs: {ost_errors[1:]!r}")

    # Coordinate output: non-Point/EMPTY rows are null, not dropped.
    write_input(
        input_dir / "coordinate-output.csv",
        ["id", "wkt"],
        [
            ["1", "SRID=2056;POINT (1 2)"],
            ["2", "POINT Z (3 4 5)"],
            ["3", "POINT M (6 7 8)"],
            ["4", "LINESTRING (0 0, 1 1)"],
            ["5", "POLYGON ((0 0, 1 0, 1 1, 0 0))"],
            ["6", "POINT EMPTY"],
            ["7", "POINT (9 10)"],
        ],
    )
    run_converter_pipeline(hop_home, "coordinate-output.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "coordinate-output.csv",
        [
            ["id", "x_out", "y_out"],
            ["1", "1.0", "2.0"],
            ["2", "3.0", "4.0"],
            ["3", "6.0", "7.0"],
            ["4", "", ""],
            ["5", "", ""],
            ["6", "", ""],
            ["7", "9.0", "10.0"],
        ],
    )

    # Coordinate errors: only a fully NULL tuple is a normal null row.
    write_input(
        input_dir / "coordinate-errors.csv",
        ["id", "x", "y", "z", "m"],
        [
            ["1", "1", "2", "", ""],
            ["2", "", "", "", ""],
            ["3", "1", "", "", ""],
            ["4", "NaN", "2", "", ""],
            ["6", "3", "4", "5", "6"],
        ],
    )
    run_converter_pipeline(hop_home, "coordinate-errors.hpl", env, input_dir, output_dir)
    require_output(
        output_dir / "coordinate-errors-normal.csv",
        [
            ["id", "ewkt"],
            ["1", "SRID=2056;POINT ZM (1 2 NaN NaN)"],
            ["2", ""],
            ["6", "SRID=2056;POINT ZM (3 4 5 6)"],
        ],
    )
    coordinate_errors = read_output(output_dir / "coordinate-errors.csv")
    if [row[0] for row in coordinate_errors[1:]] != ["3", "4"]:
        raise SystemExit(f"Unexpected coordinate error IDs: {coordinate_errors[1:]!r}")
    if any("GC_" not in row[-1] for row in coordinate_errors[1:]):
        raise SystemExit(f"Missing coordinate error code: {coordinate_errors[1:]!r}")

    print("Installed Hop Geometry Converter E2E OK", flush=True)



def run_reproject_e2e(
    hop_home: Path, env: dict[str, str], input_dir: Path, output_dir: Path, work: Path
) -> None:
    # Only Hop itself belongs on this launcher's classpath. Plugins must be found
    # by Hop's real registry and must share Geometry Type's runtime.
    classes = work / "reproject-probe"
    classes.mkdir()
    classpath = os.pathsep.join(str(p) for p in sorted((hop_home / "lib").rglob("*.jar")))
    java_home = Path(env.get("JAVA_HOME", ""))
    javac = java_home / "bin/javac"
    java = java_home / "bin/java"
    if not javac.is_file() or not java.is_file():
        javac = Path(shutil.which("javac") or "javac")
        java = Path(shutil.which("java") or "java")
    subprocess.run(
        [str(javac), "-proc:none", "-cp", classpath, "-d", str(classes),
         str(ROOT / "e2e/geometry-reproject/RuntimeIdentityProbe.java")],
        check=True, env=env, timeout=120,
    )
    subprocess.run(
        [str(java), "-cp", str(classes) + os.pathsep + classpath, "RuntimeIdentityProbe"],
        check=True, env=env, cwd=hop_home, timeout=180,
    )
    source_rows = [
        ["1", "SRID=4326;POINT ZM (7 47 123 456)"],
        ["2", "POINT (7 47)"],
        ["3", "SRID=4326;CIRCULARSTRING (7 47, 8 48, 9 47)"],
        ["4", "SRID=4979;POINT Z (7 47 100)"],
        ["5", "SRID=4326;POINT EMPTY"],
        ["6", "SRID=4326;POINT (0 0)"],
        ["7", "SRID=999999;POINT (7 47)"],
        ["8", ""],
    ]
    write_input(input_dir / "geometry-reproject.csv", ["id", "wkt"], source_rows)
    for mode in ("route", "null"):
        run_pipeline(hop_home, ROOT / f"e2e/geometry-reproject/{mode}.hpl",
                     env, input_dir, output_dir)
        rows = read_output(output_dir / f"reproject-{mode}.csv")
        assert rows[0] == ["id", "out"], rows
        expected_ids = ["1", "5", "6", "8"] if mode == "route" else [str(i) for i in range(1, 9)]
        assert [r[0] for r in rows[1:]] == expected_ids, rows
        values = {r[0]: r[1] for r in rows[1:]}
        match = re.fullmatch(r"SRID=3857;POINT ZM \(([^ ]+) ([^ ]+) 123 456\)", values["1"])
        assert match, values
        assert math.isclose(float(match[1]), 779236.435552915, rel_tol=0, abs_tol=1e-6), values
        assert math.isclose(float(match[2]), 5942074.072431108, rel_tol=0, abs_tol=1e-6), values
        assert values["5"] == "SRID=3857;POINT EMPTY", values
        match = re.fullmatch(r"SRID=3857;POINT \(([^ ]+) ([^ ]+)\)", values["6"])
        assert match and all(abs(float(v)) < 1e-6 for v in match.groups()), values
        assert values["8"] == "", values
        if mode == "null":
            assert all(values[i] == "" for i in ("2", "3", "4", "7")), values
    errors = read_output(output_dir / "reproject-errors.csv")
    assert errors[0] == ["id", "wkt", "errors", "error_desc", "error_fields", "error_codes"], errors
    assert [r[0] for r in errors[1:]] == ["2", "3", "4", "7"], errors
    original = dict(source_rows)
    for row in errors[1:]:
        assert row[1] == original[row[0]] and row[2] == "1", row
        assert row[3] and row[4] == "geometry" and row[5], row
    print("Installed Geometry Reproject EPSG, XYZM, EMPTY, NULL and error routing OK", flush=True)


def make_legacy_geometry_zip(geometry_zip: Path, target: Path) -> Path:
    """Create a test-only Geometry ZIP whose plugin JAR lacks GeometryCodec."""
    with zipfile.ZipFile(geometry_zip) as source, zipfile.ZipFile(
        target, "w", compression=zipfile.ZIP_DEFLATED
    ) as destination:
        for entry in source.infolist():
            payload = source.read(entry.filename)
            if entry.filename.endswith(".jar") and "/hop-geometry-type/" in entry.filename:
                rewritten = io.BytesIO()
                with zipfile.ZipFile(
                    io.BytesIO(payload)
                ) as plugin_jar, zipfile.ZipFile(
                    rewritten, "w", compression=zipfile.ZIP_DEFLATED
                ) as output_jar:
                    for jar_entry in plugin_jar.infolist():
                        if jar_entry.filename.startswith(
                            "com/atolcd/hop/gis/geometry/codec/GeometryCodec"
                        ):
                            continue
                        output_jar.writestr(jar_entry, plugin_jar.read(jar_entry.filename))
                payload = rewritten.getvalue()
            destination.writestr(entry, payload)
    return target


def run_negative_installation(
    hop_home: Path,
    plugin_zip: Path,
    geometry_zip: Path,
    env: dict[str, str],
    input_dir: Path,
    work: Path,
) -> None:
    if (hop_home / PLUGIN_ROOT).exists() or (hop_home / GEOMETRY_ROOT).exists():
        raise SystemExit(f"Negative Hop home is not clean: {hop_home}")
    legacy_zip = make_legacy_geometry_zip(geometry_zip, work / "legacy-geometry.zip")
    extract_plugin(legacy_zip, hop_home, GEOMETRY_ROOT)
    extract_plugin(plugin_zip, hop_home, PLUGIN_ROOT)

    write_input(
        input_dir / "negative-smoke.csv",
        ["id", "wkt"],
        [["1", "POINT (1 2)"]],
    )
    output_file = input_dir.parent / "negative-output" / "negative-smoke.csv"
    output_file.parent.mkdir(exist_ok=True)
    if output_file.exists():
        output_file.unlink()
    command = [
        str(hop_home / "hop-run.sh"),
        "-r",
        "local",
        "-f",
        str(ROOT / "e2e/geometry-converter/negative-smoke.hpl"),
        "-p",
        f"E2E_INPUT_DIR={input_dir}",
        "-p",
        f"E2E_OUTPUT_DIR={output_file.parent}",
    ]
    print("==>", " ".join(command), flush=True)
    result = subprocess.run(
        command, check=False, env=env, text=True, capture_output=True, timeout=300
    )
    combined = result.stdout + "\n" + result.stderr
    if result.returncode == 0:
        raise SystemExit("Negative installation unexpectedly succeeded.\n" + combined)
    if "GeometryCodec" not in combined and "updated Geometry Type" not in combined:
        raise SystemExit(
            "Negative installation failed without a clear GeometryCodec upgrade message.\n"
            + combined
        )
    if output_file.exists() and output_file.stat().st_size > 0:
        raise SystemExit("Negative installation produced normal output despite missing GeometryCodec")
    print("Negative GeometryCodec installation rejected as expected", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--hop-home", required=True, type=Path)
    parser.add_argument("--plugin-zip", required=True, type=Path)
    parser.add_argument("--geometry-zip", required=True, type=Path)
    parser.add_argument("--negative-hop-home", type=Path)
    args = parser.parse_args()

    hop_home = args.hop_home.resolve()
    if not (hop_home / "hop-run.sh").is_file():
        raise SystemExit(f"Not an Apache Hop home: {hop_home}")
    if (hop_home / PLUGIN_ROOT).exists():
        raise SystemExit(f"Hop home already contains Geoprocessing: {hop_home / PLUGIN_ROOT}")
    if (hop_home / GEOMETRY_ROOT).exists():
        raise SystemExit(f"Hop home already contains Geometry Type: {hop_home / GEOMETRY_ROOT}")

    with tempfile.TemporaryDirectory(prefix="hop-geoprocessing-e2e-") as temporary:
        work = Path(temporary)
        config = work / "config"
        audit = work / "audit"
        input_dir = work / "input"
        output_dir = work / "output"
        input_dir.mkdir()
        output_dir.mkdir()
        (config / "metadata/pipeline-run-configuration").mkdir(parents=True)
        audit.mkdir()
        (config / "metadata/pipeline-run-configuration/local.json").write_text(
            '{\n'
            '  "name": "local",\n'
            '  "engineRunConfiguration": {"Local": {"rowset_size": "2", "safe_mode": true}},\n'
            '  "configurationVariables": []\n'
            '}\n',
            encoding="utf-8",
        )
        (input_dir / "geometry.csv").write_text(
            "geometry\nPOINT (3 4)\nPOINT (7 8)\n",
            encoding="utf-8",
        )

        extract_plugin(args.geometry_zip, hop_home, GEOMETRY_ROOT)
        extract_plugin(args.plugin_zip, hop_home, PLUGIN_ROOT)

        env = os.environ.copy()
        env["HOP_CONFIG_FOLDER"] = str(config)
        env["HOP_AUDIT_FOLDER"] = str(audit)
        if env.get("JAVA_HOME"):
            env["HOP_JAVA_HOME"] = env["JAVA_HOME"]

        run_pipeline(hop_home, ROOT / "e2e/geoprocessing.hpl", env, input_dir, output_dir)
        check_output(output_dir / "geoprocessing.csv")
        def curve(kind, points):
            return struct.pack("<BII", 1, kind, len(points)) + b"".join(struct.pack("<dd", *p) for p in points)
        def collection(kind, parts):
            return struct.pack("<BII", 1, kind, len(parts)) + b"".join(parts)
        # SQL/MM WKB exercises the shared exact-curve binary parser.
        lower=collection(10,[collection(9,[curve(8,[(5,0),(0,5),(-5,0)]),curve(2,[(-5,0),(-5,-10),(5,-10),(5,0)])])])
        upper=collection(10,[collection(9,[curve(8,[(-5,0),(-3,4),(0,5)]),curve(8,[(0,5),(3,4),(5,0)]),curve(2,[(5,0),(5,10),(-5,10),(-5,0)])])])
        for pipeline, geometries, expected in [
            ("linearize-curves.hpl", [curve(8,[(5,0),(0,5),(-5,0)]).hex()], "LINESTRING"),
            ("coverage-linearize.hpl", [lower.hex(),upper.hex()], "POLYGON"),
        ]:
            (input_dir / "geometry.csv").write_text("geometry\n" + "\n".join(geometries) + "\n", encoding="utf-8")
            (output_dir / "geoprocessing.csv").unlink()
            run_pipeline(hop_home, ROOT / "e2e" / pipeline, env, input_dir, output_dir)
            with (output_dir / "geoprocessing.csv").open(newline="", encoding="utf-8") as stream:
                rows = list(csv.DictReader(stream, delimiter=";"))
            if len(rows) != len(geometries) or any(not row["linearized"].startswith(expected) for row in rows):
                raise SystemExit(f"Unexpected curve linearization output: {rows!r}")

        run_converter_e2e(hop_home, env, input_dir, output_dir)
        run_reproject_e2e(hop_home, env, input_dir, output_dir, work)

        subprocess.run(
            ["python3", str(ROOT / "scripts/run-examples.py"), "--hop-home", str(hop_home)],
            check=True, env=env,
        )

        if args.negative_hop_home is not None:
            negative_hop = args.negative_hop_home.resolve()
            if not (negative_hop / "hop-run.sh").is_file():
                raise SystemExit(f"Not an Apache Hop home: {negative_hop}")
            run_negative_installation(
                negative_hop,
                args.plugin_zip.resolve(),
                args.geometry_zip.resolve(),
                env,
                input_dir,
                work,
            )

    print("Installed Hop Geoprocessing E2E OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
