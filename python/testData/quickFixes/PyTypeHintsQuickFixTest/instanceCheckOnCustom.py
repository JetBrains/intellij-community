from typing import Generic, TypeVar

class A:
    pass

T = TypeVar("T")

class D(Generic[T]):
    pass

assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">D<caret>[int]</error>)