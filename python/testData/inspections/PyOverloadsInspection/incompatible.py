from typing import overload


@overload
def <warning descr="Signature of this @overload-decorated function is not compatible with the implementation">foo</warning>() -> None:
    pass


@overload
def foo(value: str) -> str:
    pass


def foo(value):
    return None


class A:
    @overload
    def <warning descr="Signature of this @overload-decorated method is not compatible with the implementation">foo</warning>(self) -> None:
        pass

    @overload
    def foo(self, value: str) -> str:
        pass

    def foo(self, value):
        return None