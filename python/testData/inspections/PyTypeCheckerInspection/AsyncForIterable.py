from random import randint
import asyncio
import collections


class Cls(collections.AsyncIterable):
    async def __aiter__(self):
        return self

    async def __anext__(self):
        data = await Cls.fetch_data()
        if data:
            return data
        else:
            print('iteration stopped')
            raise StopAsyncIteration

    @staticmethod
    async def fetch_data():
        r = randint(1, 100)
        return r if r < 92 else False


async def coro():
    a = Cls()
    async for i in a:  # OK
        await asyncio.sleep(0.2)
        print(i)
    else:
        print('end')

    async for i in <warning descr="Expected 'collections.AsyncIterable', got 'list' instead">[]</warning>:
        pass


loop = asyncio.get_event_loop()
loop.run_until_complete(coro())
loop.close()

