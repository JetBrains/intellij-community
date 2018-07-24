import asyncio


async def foo():
    return 24


async def with_await():
    await foo()


async def gen():
    return foo()


async def wrap(co):
    await_co = await co
    return await_co


async def baz():
    cor = foo()
    return await wrap(cor)


async def wrap_twice(co):
    await_co = await co
    return await await_co


async def bar():
    return await wrap_twice(gen())


loop = asyncio.get_event_loop()
loop.run_until_complete(bar())
loop.run_until_complete(baz())
loop.close()
