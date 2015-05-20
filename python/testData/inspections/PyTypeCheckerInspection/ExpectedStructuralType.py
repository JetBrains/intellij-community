def f(x):
    return x.foo + x.bar()


def g(x):
    return x.lower()


def test(x):
    x.foo
    f(x)
    g(x)

    z = 'foo'
    f(<warning descr="Type 'str' doesn't have expected attributes 'foo', 'bar'">z</warning>)
    g(z)
