from typing import overload


class A:
    @overload
    def bar(self, value: None) -> None:
        pass

    @overload
    def bar(self, value: int) -> str:
        pass

    @overload
    def bar(self, value: str) -> str:
        pass

    def bar(self, value):
        return None