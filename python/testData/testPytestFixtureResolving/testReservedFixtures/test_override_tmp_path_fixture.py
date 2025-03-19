import pytest

@pytest.fixture
def tmp_path():
    return 1

def test_(tmp_<caret>path):
pass
