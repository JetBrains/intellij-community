import asyncio
from contextlib import AsyncExitStack

async def example():
    async with AsyncExitStack():
        pass

async def example_correct():
    async with AsyncExitStack():
        pass
