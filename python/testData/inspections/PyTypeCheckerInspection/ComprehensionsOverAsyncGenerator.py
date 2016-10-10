async def asyncgen():
    yield 10
async def run():
    {i for i in <warning descr="Expected 'collections.Iterable', got '__asyncgenerator[int, Any]' instead">asyncgen()</warning>}
    [i for i in <warning descr="Expected 'collections.Iterable', got '__asyncgenerator[int, Any]' instead">asyncgen()</warning>]
    {i: i ** 2 for i in <warning descr="Expected 'collections.Iterable', got '__asyncgenerator[int, Any]' instead">asyncgen()</warning>}
    (i ** 2 for i in <warning descr="Expected 'collections.Iterable', got '__asyncgenerator[int, Any]' instead">asyncgen()</warning>)
    list(i for i in <warning descr="Expected 'collections.Iterable', got '__asyncgenerator[int, Any]' instead">asyncgen()</warning>)

    dataset = {data for line in <warning descr="Expected 'collections.Iterable', got '__asyncgenerator[int, Any]' instead">asyncgen()</warning>
                    for data in <warning descr="Expected 'collections.Iterable', got '__asyncgenerator[int, Any]' instead">asyncgen()</warning>
                    if check(data)}