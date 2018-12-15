import pytest
from time import sleep
class TestPyTest:
    def testOne(self):
        print("I am test1")
        assert 5 == 2*2

    def testTwo(self):
        assert True

    def testFail(self):
        print("I will fail")
        sleep(1) # To check duration
        assert False

def testThree():
    assert 4 == 2*2

@pytest.mark.parametrize("n,nn", [(i, i * 3) for i in range(0, 5)])
def test_evens(n, nn):
    assert n % 2 == 0 or nn % 2 == 0