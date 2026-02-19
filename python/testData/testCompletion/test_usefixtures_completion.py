import pytest


@pytest.fixture
def my_fixture():
    return True


@pytest.mark.usefixtures("<caret>")
def test_abc():
    ...
