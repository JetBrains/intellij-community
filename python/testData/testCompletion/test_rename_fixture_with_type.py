import pytest


@pytest.fixture()
def some_<caret>fixture():
    return 1


def test(some_fixture: int):
    pass
