from typing import overload


class A:
    @overload
    def foo(self, value: int) -> int:
        pass

    @overload
    def foo(self, value: str) -> str:
        pass

    def foo(self, value):
        return None