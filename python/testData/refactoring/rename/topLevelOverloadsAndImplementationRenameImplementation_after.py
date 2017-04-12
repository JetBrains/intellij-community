from typing import overload


@overload
def bar(value: str) -> None:
    pass

@overload
def bar(value: int) -> str:
    pass

def bar(value):
    return None