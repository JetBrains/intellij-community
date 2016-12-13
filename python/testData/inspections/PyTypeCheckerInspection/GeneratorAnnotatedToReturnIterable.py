from typing import Iterable


def f() -> Iterable[int]:
    for i in range(10):
        yield i