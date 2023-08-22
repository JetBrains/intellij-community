import pytest


@pytest.mark.xfail(raises=ZeroDivisionError)
def test_func():
    assert 42 == int("Hello, World")
