from typing import Callable


def func() -> Callable[..., int]:
    return lambda x: 42<caret>