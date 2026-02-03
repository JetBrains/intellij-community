from typing import Any


def g(x) -> Any:
    return x


def f(x):
    y = g(x.keys())
    return y.startswith('foo')
