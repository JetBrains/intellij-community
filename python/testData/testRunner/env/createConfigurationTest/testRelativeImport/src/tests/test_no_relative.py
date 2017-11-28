
import unittest


class ModuleTest(unittest.TestCase):
    def test_no_relative(self):
        self.assertEqual(sum(2,3), 5)