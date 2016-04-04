from collections import namedtuple


class C(namedtuple('C', ['foo', 'bar'])):
    pass


def f(x):
    return x.foo, x.bar

def g():
    x = C(foo=0, bar=1)
    return f(x)


print(g())