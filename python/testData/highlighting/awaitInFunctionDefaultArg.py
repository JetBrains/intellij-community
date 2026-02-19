import asyncio

def f(a=<error descr="'await' outside async function">await</error> asyncio.sleep(3) if True else 0):
    return a

async def f2(a=<error descr="'await' outside async function">await</error> asyncio.sleep(3) if True else 0):
    return a