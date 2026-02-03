import asyncio

async def genfunc():
    yield 1

def example():
    {x <error descr="'async for' outside async function">async<caret></error> for x in genfunc()}

async def example_correct():
    {x async for x in genfunc()}
