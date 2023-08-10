def example(y):
    return [x async for x in <error descr="'await' outside async function">await</error> y]

async def example_correct(y):
    return [x async for x in await y]