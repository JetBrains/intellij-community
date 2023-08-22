<warning descr="Python version 2.7 does not support this syntax">async</warning> def asyncgen():
    <warning descr="Python version 3.5 does not support 'yield' inside async functions">yield 10</warning>
<warning descr="Python version 2.7 does not support this syntax">async</warning> def run():
    {i <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for i in asyncgen()}
    [i <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for i in asyncgen()]
    {i: i ** 2 <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for i in asyncgen()}
    (i ** 2 <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for i in asyncgen())
    list(i <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for i in asyncgen())

    dataset = {data for line in gen()
                    <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for data in line
                    if check(data)}

    dataset = {data <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for line in asyncgen()
                    <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for data in line
                    if check(data)}