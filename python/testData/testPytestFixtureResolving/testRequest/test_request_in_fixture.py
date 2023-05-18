import pytest


@pytest.fixture
def request():
    pass


@pytest.fixture
def some_fixture(req<caret>uest):
    pass
