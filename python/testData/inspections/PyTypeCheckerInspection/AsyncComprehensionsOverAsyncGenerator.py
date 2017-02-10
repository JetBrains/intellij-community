async def asyncgen():
    yield 10
async def run():
    {i async for i in asyncgen()}
    [i async for i in asyncgen()]
    {i: i ** 2 async for i in asyncgen()}
    (i ** 2 async for i in asyncgen())
    list(i async for i in asyncgen())

    dataset = {data async for line in asyncgen()
                    async for data in asyncgen()
                    if check(data)}