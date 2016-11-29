async def asyncgen():
    yield 10


async def run():
    async for i in asyncgen():
        print(i)