def test(p1):
    for x in 'foo':
        pass

    for x in <warning descr="Expected type 'collections.Iterable', got 'Literal[42]' instead">42</warning>:
        pass

    for x in <warning descr="Expected type 'collections.Iterable', got 'Union[Literal[10], Any]' instead">f('foo', p1)</warning>:
        pass


def f(c, x):
    if c:
        return 10
    else:
        return x
