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


def <warning descr="Redeclared 'utf8' defined above without usage">utf8</warning>(value):
    return None