from typing import overload


@overload
def foo(param: str) -> str:
    pass


@overload
def foo(param: int) -> int:
    pass


def foo(param: bool) -> bool:
    pass


<the_ref>foo(1)