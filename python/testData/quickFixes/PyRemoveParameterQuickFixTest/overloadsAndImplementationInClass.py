from typing import overload


class A:
    @overload
    def foo(self) -> None:
        pass

    @overload
    def foo(self, value: int) -> str:
        pass

    @overload
    def foo(self, value: str) -> str:
        pass

    def foo(self, va<caret>lue=None):
        return None