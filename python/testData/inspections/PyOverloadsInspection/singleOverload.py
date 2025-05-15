from typing import overload


@overload
def <warning descr="At least two @overload-decorated functions must be present">foo</warning>() -> None: ...


def foo() -> None:
    pass


class A:
    @overload
    def <warning descr="At least two @overload-decorated methods must be present">foo</warning>() -> None: ...

    def foo() -> None:
        pass


@overload
def bar(x: int) -> None: ...


@overload
def bar(x: str) -> None: ...


def bar(x: int | str) -> None:
    pass


class B:
    @overload
    def bar(x: int) -> None: ...

    @overload
    def bar(x: str) -> None: ...

    def bar(x: int | str) -> None:
        pass