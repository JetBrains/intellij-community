import asyncio


async def connect():
    def callback():
        return await asyncio.sleep(5)

    return await asyncio.sleep(5)
