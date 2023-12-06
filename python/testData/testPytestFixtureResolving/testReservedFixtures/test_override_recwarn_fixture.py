import pytest

@pytest.fixture
def recwarn():
    return 1

def test_(rec<caret>warn):
    pass
