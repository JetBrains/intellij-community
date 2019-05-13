from typing import overload

class C:
    @overload
    def foo(self, a: str, b: str) -> str: ...

    @overload
    def foo(self, a: int, b: int) -> int: ...