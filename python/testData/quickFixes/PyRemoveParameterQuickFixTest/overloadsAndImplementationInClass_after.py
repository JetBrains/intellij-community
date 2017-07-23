from typing import overload


class A:
    @overload
    def foo(self) -> None:
        pass

    @overload
    def foo(self) -> str:
        pass

    @overload
    def foo(self) -> str:
        pass

    def foo(self):
        return None