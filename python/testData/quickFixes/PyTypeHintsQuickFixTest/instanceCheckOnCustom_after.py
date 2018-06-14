from typing import Generic, TypeVar

class A:
    pass

T = TypeVar("T")

class D(Generic[T]):
    pass

assert isinstance(A(), D)