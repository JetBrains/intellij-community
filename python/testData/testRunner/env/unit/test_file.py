import unittest

class GoodTest(unittest.TestCase):
  def test_passes(self):
    self.assertEqual(2+2, 4)

class BadTest(unittest.TestCase):
  def test_fails(self):
    self.assertEqual(2+2, 5)

if __name__ == '__main__':
    unittest.main()
