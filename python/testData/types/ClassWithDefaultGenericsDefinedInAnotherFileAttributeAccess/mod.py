from typing import Generic, TypeVar

T = TypeVar('T', default=int)
U = TypeVar('U', default=str)

class Box(Generic[T, U]):
    val: T | U