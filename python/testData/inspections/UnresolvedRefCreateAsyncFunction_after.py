async def ref(s, t):
    pass


async def f():
    s = 3
    await ref(s, t=1)