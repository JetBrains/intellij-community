from typing import overload


@overload
def foo(param: str) -> str:
    pass


@overload
def foo(param: int) -> int:
    pass


def fo<the_ref>o(param: bool) -> bool:
    pass