from typing import overload


@overload
def foo() -> None:
    pass

@overload
def foo() -> str:
    pass

@overload
def foo() -> str:
    pass

def foo():
    return None