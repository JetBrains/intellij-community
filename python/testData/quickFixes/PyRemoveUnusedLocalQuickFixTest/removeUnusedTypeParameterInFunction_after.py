from typing import Callable

def foo[T](fn: Callable[..., T]) -> T:
    return fn()
