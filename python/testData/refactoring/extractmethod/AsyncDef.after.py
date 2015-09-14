async def foo(x):
    y = await bar(x)
    return await y


async def bar(x_new):
    y = await x_new
    return y
