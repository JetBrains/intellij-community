from typing import overload


class A:
    @overload
    def f(self) -> float:
        pass

    @overload
    def f(self, *args: int) -> str:
        pass


a: A
f = a.f