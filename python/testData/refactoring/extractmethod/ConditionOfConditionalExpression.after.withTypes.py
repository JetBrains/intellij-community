from typing import Any


def f(n):
    return n * 2 if bar(n) else n + 1


def bar(n_new) -> Any:
    return n_new