import unittest

from ..utils import util

class MyTest(unittest.TestCase):
  def test_multiply(self):
    self.assertEqual(4, util.multiply(2, 2))
