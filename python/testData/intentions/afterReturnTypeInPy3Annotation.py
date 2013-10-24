def g(x) -> object:
    return x


def f(x):
    y = g(x.keys())
    return y.startswith('foo')
