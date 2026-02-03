import asyncio

async def genfunc():
    yield 1

def example():
    <error descr="'async for' outside async function">async<caret></error> for x in genfunc():
        pass

async def example_correct():
    async for x in genfunc():
        pass
