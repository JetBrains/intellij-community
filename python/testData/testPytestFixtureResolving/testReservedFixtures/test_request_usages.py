import pytest


@pytest.fixture
def requ<caret>est():
    pass


@pytest.fixture
def some_fixture(request):
    pass


def test_simple(request):
    pass
