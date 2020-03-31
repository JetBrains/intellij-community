from typing_extensions import final
from typing import overload

class A:
    @overload
    @final
    def foo(self, a: int) -> int: ...

    @overload
    def foo(self, a: str) -> str: ...