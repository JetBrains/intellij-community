import unittest
from testData.testRunner.env.unit.dependentTests.testedCode.my_class import *

class MyClassTest(unittest.TestCase):
  def test_foo(self):
    c = MyClass()
    self.assertEquals("bar", c.foo())
