import asyncio

async def genfunc():
    yield 1

async def example():
    {x async for x in genfunc()}

async def example_correct():
    {x async for x in genfunc()}
