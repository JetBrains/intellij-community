
import unittest

from ..module import sum

class ModuleTest(unittest.TestCase):
    def test_sum(self):
        self.assertEqual(sum(2,3), 5)