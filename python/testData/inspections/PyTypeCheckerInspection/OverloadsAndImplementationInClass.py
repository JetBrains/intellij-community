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


A().foo(None)
A().foo(5)
A().foo("5")
A().foo(<warning descr="Unexpected type(s):(A)Possible types:(None)(int)(str)">A()</warning>)