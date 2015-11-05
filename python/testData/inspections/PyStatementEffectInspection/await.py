async def f(x):
    y = await x
    await x
    if await x:
        pass
    f(await x)
    <warning descr="Statement seems to have no effect">x</warning>
    return await x
