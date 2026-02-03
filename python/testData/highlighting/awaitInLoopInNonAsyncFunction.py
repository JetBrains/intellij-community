def example(x):
    for i in <error descr="'await' outside async function">await</error> x:
        yield i

async def example_correct(x):
    for i in await x:
        yield i
