async def f51():
    await<error descr="expression expected"> </error>= 5  # fail
    return (await<error descr="expression expected">)</error>  # fail


def f52():
    await = 5
    return (await)


async def f61():
    await<error descr="expression expected"> </error>= 5  # fail
    return ("a", await<error descr="expression expected">,</error> "b")  # fail


def f62():
    await = 5
    return ("a", await, "b")


async def f71(x):
    return (await x)


async def f72(x):
    return ("a", await x, "b")