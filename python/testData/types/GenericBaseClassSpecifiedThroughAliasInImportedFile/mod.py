from typing import Generic, TypeVar

T = TypeVar('T')

class Super(Generic[T]):
    pass

Alias = Super

class Sub(Alias[T]):
    pass
