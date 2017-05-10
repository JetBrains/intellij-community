from unittest import TestCase

class TestThis(TestCase):
  def test_this(self):
    with self.subTest('test'):
      raise AttributeError('should fail')