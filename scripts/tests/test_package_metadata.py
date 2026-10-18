"""The package report reads inherited metadata from the build's actual Maven cache."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('package_checker', Path(__file__).parents[1] / 'verify-package.py')
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


class PackageMetadataTest(unittest.TestCase):
    def test_property_is_read_from_resolved_parent(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'pom.xml').write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
<parent><groupId>ch.so.agi</groupId><artifactId>hop-plugin-parent</artifactId>
<version>0.1.0-SNAPSHOT</version><relativePath/></parent></project>''')
            parent = root / 'm2/ch/so/agi/hop-plugin-parent/0.1.0-SNAPSHOT/hop-plugin-parent-0.1.0-SNAPSHOT.pom'
            parent.parent.mkdir(parents=True)
            parent.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
<properties><hop.version>2.19.0</hop.version></properties></project>''')
            with patch.object(checker, 'ROOT', root):
                self.assertEqual(checker.pom_property('hop.version', root / 'm2'), '2.19.0')
                with self.assertRaisesRegex(SystemExit, 'Missing inherited Maven property'):
                    checker.pom_property('missing', root / 'm2')
                parent.unlink()
                with self.assertRaisesRegex(SystemExit, 'Missing resolved parent POM'):
                    checker.pom_property('hop.version', root / 'm2')
