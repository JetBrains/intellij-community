import time


def test_1():
    time.sleep(1)
    assert True


def test_2():
    time.sleep(2)
    assert False


def test_3():
    time.sleep(3)
    assert True
