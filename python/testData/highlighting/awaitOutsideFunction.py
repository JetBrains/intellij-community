import asyncio

a = <error descr="'await' outside async function">await</error> asyncio.sleep(1)

class C:
    b = <error descr="'await' outside async function">await</error> asyncio.sleep(1)

