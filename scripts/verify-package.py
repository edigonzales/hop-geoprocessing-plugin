#!/usr/bin/env python3
"""Validate the canonical Apache Hop Geoprocessing plugin ZIP."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
POM_NAMESPACE = "{http://maven.apache.org/POM/4.0.0}"
PLUGIN_ROOT = "plugins/transforms/hop-geoprocessing"
CORE_JAR = f"{PLUGIN_ROOT}/lib/hop-geoprocessing-core.jar"

PLUGIN_SPECS = {
    "hop-transform-geometry-reproject": (
        "ch/so/agi/hop/geoprocessing/transform/geometryreproject/GeometryReprojectMeta.class",
        "ch/so/agi/hop/geoprocessing/transform/geometryreproject/icons/geometry-reproject.svg",
    ),
    "hop-transform-geometry-ops": (
        "ch/so/agi/hop/geoprocessing/transform/geometryops/GeometryOperationMeta.class",
        "ch/so/agi/hop/geoprocessing/transform/geometryops/GeoprocessingClassLoaderBootstrap.class",
        "ch/so/agi/hop/geoprocessing/transform/geometryops/icons/geometry-operation.svg",
    ),
    "hop-transform-coverage-ops": (
        "ch/so/agi/hop/geoprocessing/transform/coverageops/CoverageOperationMeta.class",
        "ch/so/agi/hop/geoprocessing/transform/coverageops/icons/coverage-operation.svg",
    ),
    "hop-transform-spatial-predicate": (
        "ch/so/agi/hop/geoprocessing/transform/spatialpredicate/SpatialPredicateMeta.class",
        "ch/so/agi/hop/geoprocessing/transform/spatialpredicate/icons/spatial-predicate.svg",
    ),
    "hop-transform-layer-overlay": (
        "ch/so/agi/hop/geoprocessing/transform/layeroverlay/LayerOverlayMeta.class",
        "ch/so/agi/hop/geoprocessing/transform/layeroverlay/icons/layer-overlay.svg",
    ),
    "hop-transform-layer-aggregate": (
        "ch/so/agi/hop/geoprocessing/transform/layeraggregate/LayerAggregateMeta.class",
        "ch/so/agi/hop/geoprocessing/transform/layeraggregate/icons/layer-aggregate.svg",
    ),
    "hop-transform-geometry-converter": (
        "ch/so/agi/hop/geoprocessing/transform/geometryconverter/GeometryConverterMeta.class",
        "ch/so/agi/hop/geoprocessing/transform/geometryconverter/icons/geometry-converter.svg",
    ),
}

FORBIDDEN_SHARED_LIBRARIES = {
    "hop-geometry-type.jar",
    "jts-core.jar",
    "geolatte-geom.jar",
    "slf4j-api.jar",
}


def project_version() -> str:
    root = ET.parse(ROOT / "pom.xml").getroot()
    version = root.findtext(f"{POM_NAMESPACE}version") or root.findtext("version")
    if not version:
        raise SystemExit("Could not resolve project.version from pom.xml")
    return version


def pom_property(name: str, maven_repository: Path) -> str:
    """Read inherited properties from the same published parent Maven resolved."""
    pom = ROOT / "pom.xml"
    visited = set()
    while pom not in visited:
        visited.add(pom)
        root = ET.parse(pom).getroot()
        value = root.findtext(f"{POM_NAMESPACE}properties/{POM_NAMESPACE}{name}")
        if value:
            return value
        parent = root.find(f"{POM_NAMESPACE}parent")
        if parent is None:
            break
        coordinates = [parent.findtext(f"{POM_NAMESPACE}{part}")
                       for part in ("groupId", "artifactId", "version")]
        if not all(coordinates):
            break
        group, artifact, version = coordinates
        pom = maven_repository / group.replace(".", "/") / artifact / version / f"{artifact}-{version}.pom"
        if not pom.is_file():
            raise SystemExit(f"Missing resolved parent POM: {pom}; pass the build's --maven-repository")
    raise SystemExit(f"Missing inherited Maven property: {name}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_safe_path(name: str) -> None:
    path = Path(name)
    if path.is_absolute() or ".." in path.parts:
        raise SystemExit(f"ZIP contains an unsafe path: {name}")


def validate_jar(name: str, content: bytes, required: tuple[str, ...]) -> None:
    with zipfile.ZipFile(io.BytesIO(content)) as jar:
        if jar.testzip() is not None:
            raise SystemExit(f"JAR is corrupt: {name}")
        entries = set(jar.namelist())
        forbidden_prefixes = ("org/apache/hop/", "org/eclipse/swt/")
        if any(entry.startswith(forbidden_prefixes) for entry in entries if entry.endswith(".class")):
            raise SystemExit(f"JAR embeds Hop or SWT classes: {name}")
        missing = sorted(set(required) - entries)
        if missing:
            raise SystemExit(f"JAR {name} is missing required entries: {missing}")
        if "META-INF/jandex.idx" not in entries:
            raise SystemExit(f"JAR is missing Jandex metadata: {name}")


def validate(path: Path, version: str, maven_repository: Path) -> dict[str, object]:
    expected_name = f"hop-geoprocessing-plugin-{version}.zip"
    if not path.is_file():
        raise SystemExit(f"Missing package ZIP: {path}")
    if path.name != expected_name:
        raise SystemExit(f"Unexpected package name {path.name!r}; expected {expected_name!r}")

    expected_plugin_jars = {
        f"{PLUGIN_ROOT}/{artifact_id}-{version}.jar" for artifact_id in PLUGIN_SPECS
    }
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise SystemExit(f"Package ZIP is corrupt: {path}")
        entries = archive.namelist()
        for entry in entries:
            check_safe_path(entry)
        files = {entry for entry in entries if not entry.endswith("/")}

        plugin_jars = {
            entry
            for entry in files
            if entry.startswith(f"{PLUGIN_ROOT}/")
            and entry.endswith(".jar")
            and "/lib/" not in entry
        }
        if plugin_jars != expected_plugin_jars:
            raise SystemExit(
                f"Expected exactly {len(PLUGIN_SPECS)} geoprocessing plugin JARs, found {sorted(plugin_jars)}"
            )

        core_jars = {
            entry for entry in files if entry.startswith(f"{PLUGIN_ROOT}/lib/") and entry.endswith(".jar")
        }
        if core_jars != {CORE_JAR}:
            raise SystemExit(f"Expected exactly {CORE_JAR}, found {sorted(core_jars)}")

        forbidden = sorted(
            entry
            for entry in files
            if entry.startswith(f"{PLUGIN_ROOT}/lib/")
            and Path(entry).name in FORBIDDEN_SHARED_LIBRARIES
        )
        if forbidden:
            raise SystemExit(f"Package contains shared Geometry Type runtime files: {forbidden}")

        plugin_hashes: dict[str, str] = {}
        for artifact_id, required in PLUGIN_SPECS.items():
            jar_name = f"{PLUGIN_ROOT}/{artifact_id}-{version}.jar"
            content = archive.read(jar_name)
            validate_jar(jar_name, content, required)
            plugin_hashes[jar_name] = hashlib.sha256(content).hexdigest()

        core_content = archive.read(CORE_JAR)
        validate_jar(CORE_JAR, core_content, ())
        core_hash = hashlib.sha256(core_content).hexdigest()

    return {
        "schemaVersion": 1,
        "version": version,
        "hopVersion": pom_property("hop.version", maven_repository),
        "geometryTypeVersion": pom_property("hop.geometry.type.version", maven_repository),
        "zipFile": str(path),
        "sha256": sha256(path),
        "pluginRoot": PLUGIN_ROOT,
        "pluginJars": plugin_hashes,
        "coreJar": CORE_JAR,
        "coreJarSha256": core_hash,
    }


def main() -> int:
    version = project_version()
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--zip",
        type=Path,
        default=ROOT
        / f"assemblies/assemblies-hop-geoprocessing-suite/target/hop-geoprocessing-plugin-{version}.zip",
    )
    parser.add_argument("--maven-repository", type=Path,
                        default=Path(os.environ.get("MAVEN_REPO_LOCAL", Path.home() / ".m2/repository")),
                        help="Local Maven repository used by the build (for inherited parent metadata)")
    args = parser.parse_args()
    report = validate(args.zip, version, args.maven_repository)
    output = ROOT / "target/package-verification.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
