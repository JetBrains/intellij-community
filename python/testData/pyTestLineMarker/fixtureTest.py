import pytest

@pytest.fixture
def test_fixture():
    return 42

def test_actual():
    assert True