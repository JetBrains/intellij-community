import pytest


@pytest.fixture()
def fixture_generator():
    yield "Hello World"


def test_(fixture_generator):
    assert fixture<caret>_generator == "Hello World"
