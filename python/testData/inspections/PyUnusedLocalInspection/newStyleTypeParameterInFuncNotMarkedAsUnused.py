from typing import Callable

def wait_for_condition[T](fn: Callable[..., T]) -> T:
    return fn()

def foo[T, U](fn: Callable[..., T]) -> U:
    return fn()

def bar[T, <weak_warning descr="Type parameter 'U' is not used">U</weak_warning>](fn: Callable[..., T]) -> T:
    return fn()

def baz[<weak_warning descr="Type parameter 'T' is not used">T</weak_warning>, <weak_warning descr="Type parameter 'U' is not used">U</weak_warning>](fn: Callable[..., int]) -> str:
    return fn()