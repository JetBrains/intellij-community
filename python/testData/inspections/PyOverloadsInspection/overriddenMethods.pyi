from typing import override, overload

class A:
    @override
    @overload
    def foo(self, a: int) -> int: ...

    @overload
    def foo(self, a: str) -> str: ...

    @overload
    def bar(self, a: int) -> int: ...

    @override
    @overload
    def <warning descr="'@override' should be placed only on the first overload">bar</warning>(self, a: str) -> str: ...
