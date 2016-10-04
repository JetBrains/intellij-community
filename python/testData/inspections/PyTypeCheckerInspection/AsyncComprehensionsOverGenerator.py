def gen():
    yield 10
async def run():
    {i async for i in <warning descr="Expected 'collections.AsyncIterable', got '__generator[int, Any, None]' instead">gen()</warning>}
    [i async for i in <warning descr="Expected 'collections.AsyncIterable', got '__generator[int, Any, None]' instead">gen()</warning>]
    {i: i ** 2 async for i in <warning descr="Expected 'collections.AsyncIterable', got '__generator[int, Any, None]' instead">gen()</warning>}
    (i ** 2 async for i in <warning descr="Expected 'collections.AsyncIterable', got '__generator[int, Any, None]' instead">gen()</warning>)
    list(i async for i in <warning descr="Expected 'collections.AsyncIterable', got '__generator[int, Any, None]' instead">gen()</warning>)

    dataset = {data async for line in <warning descr="Expected 'collections.AsyncIterable', got '__generator[int, Any, None]' instead">gen()</warning>
                    async for data in <warning descr="Expected 'collections.AsyncIterable', got '__generator[int, Any, None]' instead">gen()</warning>
                    if check(data)}