import pytest

@pytest.fixture
def tmpdir_factory():
    return 1

def test_(tmpdir_<caret>factory):
    pass
