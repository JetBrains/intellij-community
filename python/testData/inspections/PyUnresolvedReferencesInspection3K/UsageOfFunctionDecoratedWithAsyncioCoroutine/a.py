import asyncio


@asyncio.coroutine
def foo():
    yield from asyncio.sleep(1)
    return 3

async def bar():
    return await foo() * 2