from typing import Generic, TypeVar

T = TypeVar('T')


class Holder(Generic[T]):
    def __init__(self, x: T):
        pass

    def get(self) -> T:
        pass