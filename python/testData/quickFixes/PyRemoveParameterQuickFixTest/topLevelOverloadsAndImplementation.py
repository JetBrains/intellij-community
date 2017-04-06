from typing import overload


@overload
def foo() -> None:
    pass

@overload
def foo(value: int) -> str:
    pass

@overload
def foo(value: str) -> str:
    pass

def foo(va<caret>lue=None):
    return None