import unittest
from .testedCode.my_class import *

class MyClassTest(unittest.TestCase):
  def test_foo(self):
    c = MyClass()
    self.assertEqual("bar", c.foo())
