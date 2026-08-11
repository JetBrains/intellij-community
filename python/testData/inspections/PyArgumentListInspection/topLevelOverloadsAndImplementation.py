from typing import overload


@overload
def foo(value: None) -> None:
    pass

@overload
def foo(value: int) -> str:
    pass

@overload
def foo(value: str) -> str:
    pass

def foo(value):
    return None


foo(<warning descr="No overload of 'foo' matches the arguments. Argument types: (). Expected one of: (value: None), (value: int), (value: str)">)</warning>