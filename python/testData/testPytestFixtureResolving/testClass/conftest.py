import pytest

@pytest.fixture
def some_fixture():
    return 'fixture from conftest.py'
