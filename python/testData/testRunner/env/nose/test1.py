from unittest import TestCase

class TestNose:
    def testOne(self):
        assert 4 == 2*2

    def testTwo(self):
        assert True

def testThree():
    assert 4 == 2*2

class FooTest(TestCase):

    @classmethod
    def setUpClass(self):
        raise ValueError() # or any other bug
    def test_test(self):
        pass