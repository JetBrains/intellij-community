from typing import Type

import my
from my import X


def foo(a) -> Type[X]:
    if a:
        return my.X<caret>
    else:
        return my.Y