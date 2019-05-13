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

def foo(<weak_warning descr="Parameter 'value' value is not used">value</weak_warning>):
    return None