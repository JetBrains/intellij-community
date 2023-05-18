import pytest

@pytest.fixture
def tmpdir():
    return 1

def test_(tmp<caret>dir):
    pass
