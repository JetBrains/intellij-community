from typing import Callable

def foo[T, <caret>U](fn: Callable[..., T]) -> T:
    return fn()
