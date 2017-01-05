from typing import Generic, TypeVar

T = TypeVar('T')


class BaseClass(Generic[T]):
    def __init__(self, test: T):
        self.test = test


base = BaseClass([1, 2, 3])
base.tes<caret>