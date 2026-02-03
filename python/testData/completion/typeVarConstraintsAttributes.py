from typing import TypeVar


T = TypeVar('T', str, int)

def func(x: T):
    x.<caret>
