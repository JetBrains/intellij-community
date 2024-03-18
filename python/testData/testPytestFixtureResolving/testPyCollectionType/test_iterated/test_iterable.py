from typing import Iterable
import pytest


@pytest.fixture()
def fixture_iterable() -> Iterable[str]:
    yield "Hello World"


def test_(fixture_iterable):
    assert fixture<caret>_iterable == "Hello World"
