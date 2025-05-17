from typing import final, overload

class A:
    @final
    @overload
    def foo(self, a: int) -> int: ...

    @overload
    def foo(self, a: str) -> str: ...

    @overload
    def bar(self, a: int) -> int: ...

    @final
    @overload
    def <warning descr="'@final' should be placed only on the first overload">bar</warning>(self, a: str) -> str: ...
