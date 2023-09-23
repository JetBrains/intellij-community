import pytest


@pytest.fixture
def foo():
    return 1


def test_(foo):
    assert foo == 1