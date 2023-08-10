from typing import overload


class C:
    @overload
    def method(self, x: int) -> int:
        pass

    @overload
    def method(self, x: str) -> str:
        pass

    @overload
    def method(self, x: object) -> object:
        pass