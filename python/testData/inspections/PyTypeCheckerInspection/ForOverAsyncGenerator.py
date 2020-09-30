async def asyncgen():
    yield 10


async def run():
    for i in <warning descr="Expected type 'collections.Iterable', got 'AsyncGenerator[int, Any]' instead">asyncgen()</warning>:
        print(i)