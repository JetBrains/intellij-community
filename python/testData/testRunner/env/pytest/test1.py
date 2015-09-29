from time import sleep
class TestPyTest:
    def testOne(self):
        sleep(1) # To check duration
        assert 4 == 2*2

    def testTwo(self):
        assert True

def testThree():
    assert 4 == 2*2
