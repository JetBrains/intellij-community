import pytest

@pytest.fixture
def doctest_namespace():
    return 1

def test_(doctest_<caret>namespace):
    pass
