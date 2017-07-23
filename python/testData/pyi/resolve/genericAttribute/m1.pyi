from typing import Generic, TypeVar, Any


T = TypeVar('T')


class Concrete:
    foo = ...  # type: Any


class C(Generic[T]):
    def __init__(self, x: T): ...
    def get(self) -> T: ...
