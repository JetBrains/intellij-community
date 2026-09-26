import asyncio

async def genfunc():
    yield 1

async def example():
    async for x in genfunc():
        pass

async def example_correct():
    async for x in genfunc():
        pass
