from typing import Type

import my
from my import X, Y


def foo(a) -> Type[X | Y]:
    if a:
        return my.X<caret>
    else:
        return my.Y