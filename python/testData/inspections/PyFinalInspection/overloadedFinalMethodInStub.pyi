from typing import overload
from typing_extensions import final

class A:
    @final
    @overload
    def foo(self, a: int) -> int: ...

    @overload
    def foo(self, a: str) -> str: ...

class B:
    @overload
    def foo(self, a: int) -> int: ...

    @final
    @overload
    def <warning descr="'@final' should be placed on the first overload">foo</warning>(self, a: str) -> str: ...
