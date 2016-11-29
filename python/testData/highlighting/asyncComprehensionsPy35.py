async def asyncgen():
    <error descr="Python version 3.5 does not support 'yield' inside async functions">yield 10</error>
async def run():
    {i <error descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</error> for i in asyncgen()}
    [i <error descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</error> for i in asyncgen()]
    {i: i ** 2 <error descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</error> for i in asyncgen()}
    (i ** 2 <error descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</error> for i in asyncgen())
    list(i <error descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</error> for i in asyncgen())

    dataset = {data for line in gen()
                    <error descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</error> for data in line
                    if check(data)}

    dataset = {data <error descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</error> for line in asyncgen()
                    <error descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</error> for data in line
                    if check(data)}