import pytest


@pytest.fixture
async def foo():
    return {"key": "val"}


async def test_(foo):
    assert "val" in f<caret>oo.values()
