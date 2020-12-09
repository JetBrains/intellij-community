from typing import TypeVar


T = TypeVar('T', bound=str)

def func(x: T):
    x.upper()
