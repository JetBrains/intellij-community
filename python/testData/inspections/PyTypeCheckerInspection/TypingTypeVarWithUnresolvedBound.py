from typing import TypeVar


T = TypeVar('T', int, unresolved)


def calc(a: T, b: T):
    pass


calc('a', 0) # OK: 'unresolved' is treated as 'Any'
