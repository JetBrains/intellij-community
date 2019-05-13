from typing import TypeVar, Generic, Tuple

T1 = TypeVar('T1')
T2 = TypeVar('T2')
T3 = TypeVar('T3')


class Base(Generic[T1, T2, T3]):
    def __init__(self, x: T1):
        pass

    def m(self, x: T3) -> Tuple[T1, T2, T3]:
        pass