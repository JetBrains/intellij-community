from typing import TypeVar


T = TypeVar('T', int, unresolved)


def calc(a: T, b: T):
    pass


calc('a', <weak_warning descr="Expected type 'str' (matched generic type 'T'), got 'int' instead">0</weak_warning>)
