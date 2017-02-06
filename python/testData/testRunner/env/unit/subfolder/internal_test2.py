import unittest

class SubTest(unittest.TestCase):
  def test_in_subfolder(self):
    self.assertEquals("foo", "fo" + "o")

    