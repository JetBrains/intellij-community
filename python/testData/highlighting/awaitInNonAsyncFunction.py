import asyncio

def await_example():
    <error descr="'await' outside async function">await<caret></error> asyncio.sleep(1)

async def example_correct():
    await asyncio.sleep(1)