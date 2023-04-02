import pytest

@pytest.fixture
def some_fixture():
    return 'fixture from dir_with_conftest/conftest.py'
