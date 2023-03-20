import asyncio


async def foo(y):
    return y + 1


async def print_foo():
    x = await foo(1)
    print(x)


asyncio.run(print_foo())
