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


A().foo(<warning descr="No overload of 'foo' matches the arguments. Argument types: (). Expected one of: (value: None), (value: int), (value: str)">)</warning>