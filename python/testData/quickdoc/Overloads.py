import typing


@typing.overload
def foo(p: int) -> int:
    pass


@typing.overload
def foo(p: str) -> str:
    pass


<the_ref>foo(1)