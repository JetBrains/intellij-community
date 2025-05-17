from typing import final, overload


class A:
    @overload
    def foo(self, a: int) -> int: ...

    @overload
    def foo(self, a: str) -> str: ...

    @final
    def foo(self, a):
        pass

    @final
    @overload
    def <warning descr="'@final' should be placed on the implementation">bar</warning>(self, a: int) -> int: ...

    @overload
    def bar(self, a: str) -> str: ...

    def bar(self, a):
        pass
