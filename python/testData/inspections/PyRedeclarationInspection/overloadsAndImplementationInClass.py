from typing import overload


class A:
    @overload
    def utf8(self, value: None) -> None:
        pass

    @overload
    def utf8(self, value: bytes) -> bytes:
        pass

    @overload
    def utf8(self, value: str) -> bytes:
        pass

    def utf8(self, value):
        return None