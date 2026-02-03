from typing import TypeVar, Generic

class B:
    pass


T = TypeVar('T', bound=B)


class C(Generic[T]):
    def __init__(self, foo: T):
        self.foo = foo

    def foo(self) -> T:
        return self.foo # PY-23161 "Expected type 'T', got 'B' instead" warning here