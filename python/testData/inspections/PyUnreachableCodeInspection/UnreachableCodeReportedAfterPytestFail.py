import pytest

def test_fail():
    if True == False:
        pytest.fail()
        <warning descr="This code is unreachable">print("should be reported as unreachable")</warning>
    else:
        return 1