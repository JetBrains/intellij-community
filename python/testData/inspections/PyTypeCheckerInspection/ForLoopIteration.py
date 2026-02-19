def test(p1):
    for x in 'foo':
        pass

    for x in <warning descr="Expected type 'collections.Iterable', got 'int' instead">42</warning>:
        pass

    for x in <warning descr="Expected type 'collections.Iterable', got 'Union[int, Any]' instead">f('foo', p1)</warning>:
        pass


def f(c, x):
    if c:
        return 10
    else:
        return x
