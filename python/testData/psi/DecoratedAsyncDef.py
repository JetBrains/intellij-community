@foo
async def bar():
    await x
    return 0


@baz(x, y)
async def quux():
    return await x
