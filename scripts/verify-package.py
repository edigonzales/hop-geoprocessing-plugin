#!/usr/bin/env python3
"""Validate the canonical Apache Hop Geoprocessing plugin ZIP."""

import argparse
import io
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
PLUGIN_ROOT = "plugins/transforms/hop-geoprocessing"

# Artifact suffix, Java package, class stem and icon name for each transform.
TRANSFORMS = (
    ("geometry-reproject", "geometryreproject", "GeometryReproject", "geometry-reproject"),
    ("geometry-ops", "geometryops", "GeometryOperation", "geometry-operation"),
    ("coverage-ops", "coverageops", "CoverageOperation", "coverage-operation"),
    ("spatial-predicate", "spatialpredicate", "SpatialPredicate", "spatial-predicate"),
    ("layer-overlay", "layeroverlay", "LayerOverlay", "layer-overlay"),
    ("layer-aggregate", "layeraggregate", "LayerAggregate", "layer-aggregate"),
    ("geometry-converter", "geometryconverter", "GeometryConverter", "geometry-converter"),
)
LIBRARIES = {
    "hop-geoprocessing-core.jar": {"META-INF/jandex.idx"},
    "hop-plugin-commons-core.jar": {"ch/so/agi/hop/commons/core/ValueOrField.class"},
    "hop-plugin-commons-ui.jar": {"ch/so/agi/hop/commons/ui/ValueOrFieldControl.class"},
}
SHARED_CLASS_PACKAGES = (
    "org/apache/hop/", "org/eclipse/swt/", "org/locationtech/jts/", "com/atolcd/hop/gis/geometry/",
)


def validate_jar(archive, path, required):
    with zipfile.ZipFile(io.BytesIO(archive.read(path))) as jar:
        corrupt = jar.testzip()
        if corrupt is not None:
            raise SystemExit(f"{path} contains a corrupt entry: {corrupt}")
        entries = set(jar.namelist())
        for entry in entries:
            if entry.endswith(".class") and entry.startswith(SHARED_CLASS_PACKAGES):
                raise SystemExit(f"{path} embeds a shared runtime class: {entry}")
        if missing := required - entries:
            raise SystemExit(f"{path} is missing required entries: {sorted(missing)}")


def validate(path, version):
    jars = {f"{PLUGIN_ROOT}/lib/{name}": entries for name, entries in LIBRARIES.items()}
    for artifact, package, stem, icon in TRANSFORMS:
        base = f"ch/so/agi/hop/geoprocessing/transform/{package}"
        required = {
            "META-INF/jandex.idx",
            f"{base}/{stem}.class",
            f"{base}/{stem}Meta.class",
            f"{base}/{stem}Dialog.class",
            f"{base}/icons/{icon}.svg",
        }
        if artifact == "geometry-ops":
            required.add(f"{base}/GeoprocessingClassLoaderBootstrap.class")
        if artifact == "geometry-converter":
            required.update(f"{base}/messages/messages_{locale}.properties" for locale in ("de_DE", "en_US"))
        jars[f"{PLUGIN_ROOT}/hop-transform-{artifact}-{version}.jar"] = required

    expected = {*jars, f"{PLUGIN_ROOT}/dependencies.xml"}
    directories = {"plugins/", "plugins/transforms/", f"{PLUGIN_ROOT}/", f"{PLUGIN_ROOT}/lib/"}
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        entries = set(names)
        unexpected = entries - expected - directories
        missing = expected - entries
        if missing or unexpected:
            raise SystemExit(f"Invalid package layout. Missing: {sorted(missing)}. Unexpected: {sorted(unexpected)}.")
        if len(names) != len(entries):
            raise SystemExit(f"Package contains duplicate ZIP entries: {path}")
        corrupt = archive.testzip()
        if corrupt is not None:
            raise SystemExit(f"Package contains a corrupt entry: {corrupt}")
        for name, required in jars.items():
            validate_jar(archive, name, required)


def main():
    version = ET.parse(ROOT / "pom.xml").getroot().findtext("{*}version")
    if not version:
        raise SystemExit("Missing project version in pom.xml")
    name = f"hop-geoprocessing-plugin-{version}.zip"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--zip", type=Path,
        default=ROOT / "assemblies/assemblies-hop-geoprocessing-suite/target" / name,
    )
    args = parser.parse_args()
    if args.zip.name != name:
        parser.error(f"Expected {name}, got {args.zip.name}")
    try:
        validate(args.zip, version)
    except (OSError, zipfile.BadZipFile, RuntimeError) as error:
        parser.exit(1, f"Package verification failed: {error}\n")
    print(f"Package verified: {args.zip}")


if __name__ == "__main__":
    main()
