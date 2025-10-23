from typing import Generic, TypeVar

T1 = TypeVar("T1")


class Foo(Generic[T1]): ...


class Baz:
    SOME_TYPE = Foo[int]