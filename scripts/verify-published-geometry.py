#!/usr/bin/env python3
"""Compare Maven's published Geometry JAR with the installed runtime ZIP."""

import argparse
from pathlib import Path, PurePosixPath
import xml.etree.ElementTree as ET
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip-directory", type=Path, required=True)
    parser.add_argument("--maven-repository", type=Path, required=True)
    args = parser.parse_args()

    pom = Path(__file__).resolve().parents[1] / "pom.xml"
    version = ET.parse(pom).getroot().findtext("{*}properties/{*}hop.geometry.type.version")
    if not version:
        raise SystemExit("Missing hop.geometry.type.version in pom.xml")
    zips = list(args.zip_directory.rglob("hop-geometry-type-plugin-*.zip"))
    if len(zips) != 1:
        raise SystemExit(f"Expected one published Geometry {version} ZIP in {args.zip_directory}, found {len(zips)}")

    jar = args.maven_repository / "ch/so/agi/hop-geometry-type" / version / f"hop-geometry-type-{version}.jar"
    root = PurePosixPath("plugins/misc/hop-geometry-type")
    try:
        with zipfile.ZipFile(zips[0]) as archive:
            entries = [
                entry for entry in archive.infolist()
                if not entry.is_dir()
                and PurePosixPath(entry.filename).parent == root
                and PurePosixPath(entry.filename).match("hop-geometry-type-*.jar")
            ]
            if len(entries) != 1:
                raise SystemExit(f"Expected one Geometry JAR in {zips[0]}, found {len(entries)}")
            if archive.read(entries[0]) != jar.read_bytes():
                raise SystemExit(
                    f"Maven Geometry JAR {jar} differs from {entries[0].filename} in {zips[0]}. "
                    "Re-run after Geometry publication is complete."
                )
    except (OSError, zipfile.BadZipFile, RuntimeError) as error:
        parser.exit(1, f"Geometry dependency verification failed: {error}\n")
    print(f"Published Geometry {version}: Maven JAR matches runtime ZIP")


if __name__ == "__main__":
    main()
