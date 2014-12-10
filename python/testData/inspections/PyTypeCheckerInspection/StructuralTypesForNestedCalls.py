def f(x):
    return x.foo + g(x)


def g(x):
    return x.bar


def test():
    f(<warning descr="Expected type '{foo, bar}', got 'str' instead">'string'</warning>)
