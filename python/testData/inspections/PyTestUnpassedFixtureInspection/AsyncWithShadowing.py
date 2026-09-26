import pytest
from contextlib import asynccontextmanager


class C:
    async def bla(self):
        ...


@asynccontextmanager
async def create():
    yield C()


@pytest.fixture
def client():
    pass


async def test_():
    async with create() as client:
        assert await client.bla() == 1  # no inspection warning expected