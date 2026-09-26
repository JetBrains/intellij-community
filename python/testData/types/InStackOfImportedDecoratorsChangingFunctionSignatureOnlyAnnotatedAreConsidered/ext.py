from typing import Callable, Concatenate, ParamSpec, TypeVar
             
P = ParamSpec('P')
T = TypeVar('T')
             
             
def prepend_str(fn: Callable[P, T]) -> Callable[Concatenate[str, P], tuple[str, T]]:
    def wrapper(p: str, *args, **kwargs):
        return p, fn(*args, **kwargs)
    return wrapper
             
def prepend_int(fn):
    def wrapper(p: int, *args, **kwargs):
        return p, fn(*args, **kwargs)
    return wrapper
             
def prepend_bool(fn: Callable[P, T]) -> Callable[Concatenate[bool, P], tuple[bool, T]]:
    def wrapper(p: bool, *args, **kwargs):
        return p, fn(*args, **kwargs)
    return wrapper