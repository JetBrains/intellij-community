from typing import AsyncIterable

import pytest


@pytest.fixture()
async def fixture_async_iterable() -> AsyncIterable[str]:
    yield "Hello World"


async def test_async(fixture_async_iterable):
    assert fixture_<caret>async_iterable == "Hello World"
