import asyncio

async def genfunc():
    yield 1

async def example():
    {x: x async for x in genfunc()}

async def example_correct():
    {x: x async for x in genfunc()}
