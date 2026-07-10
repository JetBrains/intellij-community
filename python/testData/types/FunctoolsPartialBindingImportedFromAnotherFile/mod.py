import functools

def foo(a: int, b: str) -> bool: ...

bound = functools.partial(foo, 1)
