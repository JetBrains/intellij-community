from typing import Iterable


def g1() -> Iterable[int]:
    for i in range(10):
        yield i


def g2() -> Iterable[int]:
    yield 42
    return None


def g3() -> Iterable:
    yield 42


def g4() -> Iterable:
    yield 42
    return None
