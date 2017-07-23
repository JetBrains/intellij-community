from typing import overload


class A:
    @overload
    def foo(self, value: None) -> None:
        pass

    @overload
    def foo(self, value: int) -> str:
        pass

    @overload
    def foo(self, value: str) -> str:
        pass

    def foo(self, value):
        return None


A().foo(<warning descr="Parameter(s) unfilledPossible callees:A.foo(self: A, value: None)A.foo(self: A, value: int)A.foo(self: A, value: str)">)</warning>