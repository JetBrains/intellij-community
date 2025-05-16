from typing import overload, override


class A:
    @overload
    def foo(self, x: int) -> None: ...

    @overload
    def foo(self, x: str) -> None: ...

    @override
    def foo(self, x: int | str) -> None:
        pass

    @overload
    def bar(self, x: int) -> None: ...

    @override
    @overload
    def <warning descr="'@override' should be placed on the implementation">bar</warning>(self, x: str) -> None: ...

    @override
    def bar(self, x: int | str) -> None:
        pass
