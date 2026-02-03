from contextlib import asynccontextmanager


@asynccontextmanager
async def func(x: int, y: str):
    yield "foo"