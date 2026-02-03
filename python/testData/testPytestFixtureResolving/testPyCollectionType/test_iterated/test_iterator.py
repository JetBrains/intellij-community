from typing import Iterator
import pytest


@pytest.fixture()
def fixture_iterator() -> Iterator[str]:
    yield "Hello World"


def test_(fixture_iterator):
    assert fixture<caret>_iterator == "Hello World"
