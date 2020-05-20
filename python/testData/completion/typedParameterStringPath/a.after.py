from typing import overload, Union
from os import PathLike

def baz(akjlkgjdfsakglkd: PathLike) -> None:
    pass

baz("foo")


def bar(akjlkgjdfsakglkd: Union[str, PathLike]) -> None:
    pass

bar("foobar.txt")


@overload
def foo(akjlkgjdfsakglkd: str) -> None:
    pass

@overload
def foo(akjlkgjdfsakglkd: PathLike) -> None:
    pass

def foo(akjlkgjdfsakglkd):
    pass

foo("foobar.txt")