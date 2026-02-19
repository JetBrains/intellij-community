import pytest

@pytest.fixture
def record_property():
    return 1

def test_(record_<caret>property):
    pass
