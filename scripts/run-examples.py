#!/usr/bin/env python3
"""Execute all public examples with installed Hop and compare their expected CSV rows."""
from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def read_csv(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.reader(stream, delimiter=';'))


def run_examples(hop_home: Path, env: dict[str, str], output: Path):
    examples = ROOT / 'examples'
    pipelines = sorted(examples.glob('*.hpl'))
    if len(pipelines) != 6 or {p.stem for p in pipelines} != {p.stem for p in (examples / 'expected').glob('*.csv')}:
        raise SystemExit('Expected six example pipelines, each with one expected CSV')
    for pipeline in pipelines:
        expected = examples / 'expected' / (pipeline.stem + '.csv')
        command = [str(hop_home / 'hop-run.sh'), '-r', 'local', '-f', str(pipeline),
                   '-p', f'OUTPUT_DIR={output}']
        print('Example:', pipeline.name, flush=True)
        result = subprocess.run(command, env=env, text=True, capture_output=True, timeout=300)
        if result.returncode:
            raise SystemExit(f'{pipeline.name} failed:\n{result.stdout}\n{result.stderr}')
        actual = output / expected.name
        if not actual.is_file() or read_csv(actual) != read_csv(expected):
            raise SystemExit(f'{pipeline.name}: expected {read_csv(expected)!r}; '
                             f'actual {read_csv(actual) if actual.exists() else "missing"!r}\n'
                             f'{result.stdout}\n{result.stderr}')
    print('All six public examples match their expected results', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--hop-home', type=Path, required=True)
    args = parser.parse_args()
    hop_home = args.hop_home.resolve()
    if not (hop_home / 'hop-run.sh').is_file():
        parser.error('Expected an installed Apache Hop home with hop-run.sh')
    with tempfile.TemporaryDirectory(prefix='geoprocessing-examples-') as tmp:
        work = Path(tmp)
        config = work / 'config/metadata/pipeline-run-configuration'
        config.mkdir(parents=True)
        (config / 'local.json').write_text(json.dumps({
            'name': 'local', 'engineRunConfiguration': {'Local': {'rowset_size': '2', 'safe_mode': True}},
            'configurationVariables': []}))
        output = work / 'output'
        output.mkdir()
        env = os.environ.copy()
        env['HOP_CONFIG_FOLDER'] = str(work / 'config')
        env['HOP_AUDIT_FOLDER'] = str(work / 'audit')
        if env.get('JAVA_HOME'):
            env['HOP_JAVA_HOME'] = env['JAVA_HOME']
        run_examples(hop_home, env, output)


if __name__ == '__main__':
    main()
