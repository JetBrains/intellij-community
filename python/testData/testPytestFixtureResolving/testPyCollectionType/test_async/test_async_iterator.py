from typing import AsyncIterator

import pytest


@pytest.fixture()
async def fixture_async_iterator() -> AsyncIterator[str]:
    yield "Hello World"


async def test_async(fixture_async_iterator):
    assert fixture_<caret>async_iterator == "Hello World"
