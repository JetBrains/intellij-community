from typing import Generic, TypeVar

T = TypeVar('T')


class MyClass(Generic[T]):
    def __init__(self, x: T):
        pass

    def __lt__(self, other: 'MyClass[T]'):
        pass


x = MyClass(1) < MyClass(2) < <weak_warning descr="Expected type 'MyClass[int]' (matched generic type 'MyClass[T]'), got 'MyClass[str]' instead">MyClass('foo')</weak_warning>
