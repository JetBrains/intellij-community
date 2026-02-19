import asyncio


async def do() -> None:
    pass


async def test() -> None:
    asyncio.create_task(do())
