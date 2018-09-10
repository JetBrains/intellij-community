import asyncio


@asyncio.coroutine
def bar():
    yield from asyncio.sleep(1)


@asyncio.coroutine
def check():
    <warning descr="Coroutine 'bar' is not awaited">bar()</warning>


async def check():
    <warning descr="Coroutine 'bar' is not awaited">bar()</warning>