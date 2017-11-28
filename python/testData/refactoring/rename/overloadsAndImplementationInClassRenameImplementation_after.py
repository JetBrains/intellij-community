from typing import overload


class A:
    @overload
    def bar(self, value: str) -> None:
        pass

    @overload
    def bar(self, value: int) -> str:
        pass

    def bar(self, value):
        return None