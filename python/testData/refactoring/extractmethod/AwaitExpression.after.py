async def foo(x):
    y = await bar(x)
    return y


async def bar(x_new):
    return await x_new + 1
