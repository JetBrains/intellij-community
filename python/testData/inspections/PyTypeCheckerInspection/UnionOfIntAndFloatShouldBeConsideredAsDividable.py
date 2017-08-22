from typing import Union


def foo(x):
    return x / (60 * 60)

bar = 0  # type: Union[int, float]
foo(bar)