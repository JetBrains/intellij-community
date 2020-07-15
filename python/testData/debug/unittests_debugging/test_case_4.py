import pytest


@pytest.fixture
def fixture():
    # noinspection PyStatementEffect
    1 / 0
    return "Hello, World"


def test_example(fixture):
    assert fixture == "Hello, World"
