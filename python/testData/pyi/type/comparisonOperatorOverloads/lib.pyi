from typing import overload, Generic, TypeVar

T = TypeVar('T')


class MyClass(Generic[T]):
    def __init__(self, x: T):
        pass

    @overload
    def __lt__(self, other: MyClass) -> T:
        pass

    @overload
    def __lt__(self, other: str) -> bool:
        pass

    def __gt__(self, other: int) -> bool:
        pass
