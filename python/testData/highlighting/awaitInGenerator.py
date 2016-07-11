async def f11(x):
    y = (await<error descr="expression expected"> </error>for await<error descr="expression expected"> </error>in [])  # fail
    await x


def f12(x):
    y = (await for await in [])
    return x


async def f21(x):
    y = (mapper(await<error descr="expression expected">)</error> for await<error descr="expression expected"> </error>in [])  # fail
    await x


def f22(x):
    y = (mapper(await) for await in [])
    return x


async def f31(x):
    await<error descr="expression expected"> </error>= []  # fail
    y = (i for i in await<error descr="expression expected">)</error>  # fail
    await x


def f32(x):
    await = []
    y = (i for i in await)
    return x


async def f41(x):
    y = (<error descr="'await' expressions are not supported here">await</error> z for z in [])  # fail
    await x


async def f42(x):
    y = (mapper(<error descr="'await' expressions are not supported here">await</error> z) for z in [])  # fail
    await x


async def f43(x):
    y = (z for <error descr="can't assign to await expression">await z</error> in [])  # fail
    await x


async def f44(x):
    y = (z for z in await x)
    await x