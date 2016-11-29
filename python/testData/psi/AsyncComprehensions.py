async def async2():
    {i async for i in asyncgen()}
    [i async for i in asyncgen()]
    {i: i ** 2 async for i in asyncgen()}
    (i ** 2 async for i in asyncgen())
    list(i async for i in asyncgen())

    dataset = {data for line in gen()
                    async for data in line
                    if check(data)}

    dataset = {data async for line in asyncgen()
                    async for data in line
                    if check(data)}