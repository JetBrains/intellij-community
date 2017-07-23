from typing import overload


@overload
def utf8(value: None) -> None:
    pass


@overload
def utf8(value: bytes) -> bytes:
    pass


@overload
def utf8(value: str) -> bytes:
    pass


def utf8(value):
    return None