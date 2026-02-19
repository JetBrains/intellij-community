import asyncio

class AsyncIterator:
    def __init__(self, data):
        pass

    def __aiter__(self):
        <error descr="'await' outside async function">await</error> asyncio.sleep(1)

    def __anext__(self):
        <error descr="'await' outside async function">await</error> asyncio.sleep(1)

