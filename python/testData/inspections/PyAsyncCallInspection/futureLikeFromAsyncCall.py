import asyncio


class FutureLike:
    def __await__(self):
        yield from asyncio.sleep(2)


async def foo():
    <warning descr="Coroutine 'FutureLike' is not awaited">FutureLike()</warning>