import pytest_asyncio


@pytest_asyncio.fixture
async def foo():
    return 1


def test(f<caret>oo):
    assert foo == 1
