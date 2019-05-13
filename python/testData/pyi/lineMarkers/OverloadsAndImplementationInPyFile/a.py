from typing import overload


class B:
    @overload
    def baz(self, v: str):
        pass

    @overload
    def baz(self, v: int):
        pass

    def baz(self, v):
        pass