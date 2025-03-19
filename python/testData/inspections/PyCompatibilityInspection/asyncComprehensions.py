<warning descr="Python version 2.7 does not support this syntax">async</warning> def asyncgen():
    yield 10
<warning descr="Python version 2.7 does not support this syntax">async</warning> def run():
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