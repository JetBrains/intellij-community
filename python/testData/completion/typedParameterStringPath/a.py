from typing import overload, Union
from os import PathLike

def baz(akjlkgjdfsakglkd: PathLike) -> None:
    pass

baz("foo<caret>")


def bar(akjlkgjdfsakglkd: Union[str, PathLike]) -> None:
    pass

bar("foo<caret>")


@overload
def foo(akjlkgjdfsakglkd: str) -> None:
    pass

@overload
def foo(akjlkgjdfsakglkd: PathLike) -> None:
    pass

def foo(akjlkgjdfsakglkd):
    pass

foo("foo<caret>")