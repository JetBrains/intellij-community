import pytest

@pytest.fixture
def pytestconfig():
    return 1

def test_(pytest<caret>config):
pass
