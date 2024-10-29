from typing import Generic, TypeVar

T = TypeVar('T')

class Base(Generic[T]):
    pass

class MyClass(Generic[T], Base[T]):
    pass