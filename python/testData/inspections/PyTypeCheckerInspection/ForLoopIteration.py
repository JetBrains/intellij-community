def test(p1):
    for x in 'foo':
        pass

    for x in <warning descr="Expected 'collections.Iterable', got 'int' instead">42</warning>:
        pass

    for x in f('foo', p1):
        pass


def f(c, x):
    if c:
        return 10
    else:
        return x
