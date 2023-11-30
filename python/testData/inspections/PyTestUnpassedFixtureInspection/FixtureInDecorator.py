import pytest


@pytest.fixture
def foo():
    return 1


@pytest.mark.usefixtures("foo")
def test_():
    assert foo == 1
