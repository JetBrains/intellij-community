def f(x):
    return x.foo + g(x)


def g(x):
    return x.bar


def test():
    f(<warning descr="Type 'str' doesn't have expected attributes 'foo', 'bar'">'string'</warning>)
