import unittest

def example_code():
    return "Hello"

class ExampleModuleTestCase(unittest.TestCase):
    def test1(self):
        self.assertEqual(example_code(), "Hello")

    def test2(self):
        self.assertTrue(True)