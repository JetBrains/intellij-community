import time

delay = 1

def test_one():
    print("Here's some passing test output.")
    time.sleep(delay)
    pass

def test_two():
    print("Here's some passing test output.")
    time.sleep(delay)
    pass

def test_three():
    print("Here's some failing test output.")
    assert False
