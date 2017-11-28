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


foo("abc")
 <ref>