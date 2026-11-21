import unittest
import tempfile
from pathlib import Path
from normalize_datop import normalize

class NormalizeTest(unittest.TestCase):
    def test_mapping_preserves_original_and_rejects_bad_data_atomically(self):
        mapping = dict(encoding="utf-8", account_column="account", time_column="time", amount_column="value",
            direction_column="flow", description_column="memo", timestamp_format="%Y%m%d%H%M%S",
            incoming_values=["deposit"], outgoing_values=["withdrawal"])
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)/"source.csv"
            output = Path(directory)/"normalized.csv"
            original = 'account,time,value,flow,memo\na,20261101100000,10.50,deposit,"hello, world"\n'
            source.write_text(original, encoding="utf-8")
            self.assertEqual(normalize(source,output,mapping),1)
            self.assertEqual(source.read_text(encoding="utf-8"),original)
            self.assertIn('a,2026-11-01T10:00:00,10.50,I,"hello, world"', output.read_text(encoding="utf-8"))
            with self.assertRaises(ValueError):
                normalize(source,output,mapping)
            source.write_text(original.replace("deposit", "unknown"),encoding="utf-8")
            bad = Path(directory)/"bad.csv"
            with self.assertRaises(ValueError):
                normalize(source,bad,mapping)
            self.assertFalse(bad.exists())

if __name__ == "__main__":
    unittest.main()
