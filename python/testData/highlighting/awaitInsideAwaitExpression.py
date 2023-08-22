import asyncio

async def f():
    await (await asyncio.sleep(1))

def f2():
    <error descr="'await' outside async function">await</error> (<error descr="'await' outside async function">await</error> asyncio.sleep(1))

<error descr="'await' outside async function">await</error> (<error descr="'await' outside async function">await</error> asyncio.sleep(1))