from typing import Generic, TypeVar

T = TypeVar('T')
V = TypeVar('V')


class Class(Generic[T, V], BaseClass1, SomeModule.SomeClass):
    pass
