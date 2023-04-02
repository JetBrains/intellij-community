import pytest

@pytest.fixture
def some_fixture():
    return 'fixture from test_root.py'

def test_simple(some_<caret>fixture):
    assert some_fixture == 'fixture from test_root.py'