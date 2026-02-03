from typing import overload


@overload
def foo(param: str) -> str:
    pass


@overload
def fo<the_ref>o(param: int) -> int:
    pass
