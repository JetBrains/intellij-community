import pytest


@pytest.fixture()
def some_fixture():
    return 1


def test(some_<caret>fixture: int):
    pass
