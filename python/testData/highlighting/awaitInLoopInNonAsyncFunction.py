def example(x):
    async for i in <error descr="'await' outside async function">await</error> x:
        yield i

async def example_correct(x):
    async for i in await x:
        yield i
