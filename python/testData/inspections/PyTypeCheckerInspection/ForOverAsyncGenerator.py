async def asyncgen():
    yield 10


async def run():
    for i in <warning descr="Expected 'collections.Iterable', got '__asyncgenerator[int, Any]' instead">asyncgen()</warning>:
        print(i)