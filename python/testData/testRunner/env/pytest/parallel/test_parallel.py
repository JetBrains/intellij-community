import unittest


class ExampleTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        print('2')

    def test_example(self):
        assert 1 == 1