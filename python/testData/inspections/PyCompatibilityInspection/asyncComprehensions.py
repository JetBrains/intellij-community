<warning descr="Python version 2.6, 2.7, 3.4 do not support this syntax">async</warning> def asyncgen():
    <warning descr="Python version 3.5 does not support 'yield' inside async functions">yield 10</warning>
<warning descr="Python version 2.6, 2.7, 3.4 do not support this syntax">async</warning> def run():
    <warning descr="Python version 2.6 does not support set comprehensions">{i <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for i in asyncgen()}</warning>
    [i <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for i in asyncgen()]
    <warning descr="Python version 2.6 does not support dictionary comprehensions">{i: i ** 2 <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for i in asyncgen()}</warning>
    (i ** 2 <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for i in asyncgen())
    list(i <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for i in asyncgen())

    dataset = <warning descr="Python version 2.6 does not support set comprehensions">{data for line in gen()
                    <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for data in line
                    if check(data)}</warning>

    dataset = <warning descr="Python version 2.6 does not support set comprehensions">{data <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for line in asyncgen()
                    <warning descr="Python version 3.5 does not support 'async' inside comprehensions and generator expressions">async</warning> for data in line
                    if check(data)}</warning>