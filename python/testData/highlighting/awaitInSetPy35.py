async def f11(x):
    y = {await<error descr="Expression expected"> </error>for await<error descr="Expression expected"> </error>in []}  # fail
    await x


def f12(x):
    y = {await for await in []}
    return x


async def f21(x):
    y = {mapper(await<error descr="Expression expected">)</error> for await<error descr="Expression expected"> </error>in []}  # fail
    await x


def f22(x):
    y = {mapper(await) for await in []}
    return x


async def f31(x):
    await<error descr="Expression expected"> </error>= []  # fail
    y = {i for i in await<error descr="Expression expected">}</error>  # fail
    await x


def f32(x):
    await = []
    y = {i for i in await}
    return x


async def f41(x):
    y = {<error descr="Python version 3.5 does not support 'await' inside comprehensions">await</error> z for z in []}  # fail
    await x


async def f42(x):
    y = {mapper(<error descr="Python version 3.5 does not support 'await' inside comprehensions">await</error> z) for z in []}  # fail
    await x


async def f43(x):
    y = {z for <error descr="Cannot assign to await expression">await z</error> in []}  # fail
    await x


async def f44(x):
    y = {z for z in await x}
    await x


async def f51():
    await<error descr="Expression expected"> </error>= 5  # fail
    return {await<error descr="Expression expected">}</error>  # fail


def f52():
    await = 5
    return {await}


async def f61():
    await<error descr="Expression expected"> </error>= 5  # fail
    return {"a", await<error descr="Expression expected">,</error> "b"}  # fail


def f62():
    await = 5
    return {"a", await, "b"}


async def f71(x):
    return {await x}


async def f72(x):
    return {"a", await x, "b"}

async def f81(x):
    {<error descr="Python version 3.5 does not support 'await' inside comprehensions">await</error> fun() for fun in funcs if <error descr="Python version 3.5 does not support 'await' inside comprehensions">await</error> smth}
    {<error descr="Python version 3.5 does not support 'await' inside comprehensions">await</error> fun() <error descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</error> for fun in funcs if <error descr="Python version 3.5 does not support 'await' inside comprehensions">await</error> smth}