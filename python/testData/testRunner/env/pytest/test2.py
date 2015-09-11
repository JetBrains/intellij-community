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

def test_evens():
    for i in range(0, 5):
        yield check_even, i, i*3

def check_even(n, nn):
    assert n % 2 == 0 or nn % 2 == 0