from typing import Type, TypeVar


class MyClass:
    class_attr = 42

    def __init__(self, attr):
        self.inst_attr = attr


T = TypeVar('T', bound=MyClass)


def func(x: Type[T]):
    x.<caret>
