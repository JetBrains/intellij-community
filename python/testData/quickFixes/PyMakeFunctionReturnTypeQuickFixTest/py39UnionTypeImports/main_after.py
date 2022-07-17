from typing import Union, Type

import my
from my import X, Y


def foo(a) -> Type[Union[X, Y]]:
    if a:
        return my.X
    else:
        return my.Y