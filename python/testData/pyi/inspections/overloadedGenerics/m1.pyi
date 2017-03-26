from typing import overload, TypeVar, Generic


def g(x: dict) -> None: ...


T = TypeVar('T')


class Gen(Generic[T]):
    def __init__(self, x: T): ...
    @overload
    def get(self, x: int, y: T) -> T: ...
    @overload
    def get(self, x: str, y: T) -> T: ...
