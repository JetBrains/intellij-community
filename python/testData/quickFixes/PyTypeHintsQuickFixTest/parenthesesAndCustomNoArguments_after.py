from typing import Generic, TypeVar

T = TypeVar("T")

class A(Generic[T]):
    def __init__(self, v):
        pass

def g(h):
    # type: (A[]) -> None
    pass