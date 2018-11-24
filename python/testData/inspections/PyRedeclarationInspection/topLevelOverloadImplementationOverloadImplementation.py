from typing import overload


@overload
def foo(value: int) -> str:
    pass


def foo(value):
    return None


@overload
def foo(value: str) -> str:
    pass


def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>(value):
    return None