class AsyncIterable:
    async def __aiter__(self):
        async def gen():
            yield 1
        return gen()

async def run():
    async for x in AsyncIterable():
                # <ref>
        pass
