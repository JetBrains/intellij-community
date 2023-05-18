import pytest

@pytest.fixture
def tmp_path_factory():
    return 1

def test_(tmp_path_<caret>factory):
    pass
