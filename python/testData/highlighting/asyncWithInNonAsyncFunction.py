import asyncio
from contextlib import AsyncExitStack

def example():
    <error descr="'async with' outside async function">async<caret></error> with AsyncExitStack():
        pass

async def example_correct():
    async with AsyncExitStack():
        pass
