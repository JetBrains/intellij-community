async def f11(x):
    y = (await<error descr="Expression expected"> </error>for <error descr="Cannot assign to await expression">await</error><error descr="Expression expected"> </error>in [])  # fail
    await x


def f12(x):
    y = (await for await in [])
    return x


async def f21(x):
    y = (mapper(await<error descr="Expression expected">)</error> for <error descr="Cannot assign to await expression">await</error><error descr="Expression expected"> </error>in [])  # fail
    await x


def f22(x):
    y = (mapper(await) for await in [])
    return x


async def f31(x):
    <error descr="Cannot assign to await expression">await</error><error descr="Expression expected"> </error>= []  # fail
    y = (i for i in await<error descr="Expression expected">)</error>  # fail
    await x


def f32(x):
    await = []
    y = (i for i in await)
    return x


async def f43(x):
    y = (z for <error descr="Cannot assign to await expression">await z</error> in [])  # fail
    await x


async def f44(x):
    y = (z for z in await x)
    await x