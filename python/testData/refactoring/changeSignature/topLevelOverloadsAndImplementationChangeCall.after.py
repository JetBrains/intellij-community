from typing import overload


@overload
def foo(value: str) -> None:
    pass

@overload
def foo(value: int) -> str:
    pass

def foo():
    return None


foo()