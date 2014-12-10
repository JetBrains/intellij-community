def f(x):
    return x.foo + x.bar()


def g(x):
    return x.lower()


def test(x):
    x.foo
    f(x)
    g(x)

    z = 'foo'
    f(<warning descr="Expected type '{foo, bar}', got 'str' instead">z</warning>)
    g(z)
