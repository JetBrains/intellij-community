from typing import Generic, TypeVar

T = TypeVar('T')


class D(Generic[T]):
    def __init__(self, attr: T):
        self.attr: T = attr


if __name__ == '__main__':
    a = D
    b = a[str]
    c = b("foo")
