import asyncio

async def async_func():
    def non_async_local(x=(await asyncio.sleep(10))):
        pass