import pytest


@pytest.fixture()
async def fixture_async_generator():
    yield "Hello World"


async def test_async(fixture_async_generator):
    assert fixture_<caret>async_generator == "Hello World"