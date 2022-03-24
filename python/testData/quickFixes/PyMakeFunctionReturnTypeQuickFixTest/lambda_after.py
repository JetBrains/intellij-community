from typing import Callable, Any


def func() -> Callable[[Any], int]:
    return lambda x: 42<caret>