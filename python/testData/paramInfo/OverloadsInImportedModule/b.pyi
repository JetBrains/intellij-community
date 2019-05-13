from typing import overload

@overload
def foo(a: str, b: str) -> str: ...

@overload
def foo(a: int, b: int) -> str: ...