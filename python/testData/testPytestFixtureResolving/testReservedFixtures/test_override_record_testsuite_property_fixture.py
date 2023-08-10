import pytest

@pytest.fixture
def record_testsuite_property():
    return 1

def test_(record_testsuite_<caret>property):
    pass
