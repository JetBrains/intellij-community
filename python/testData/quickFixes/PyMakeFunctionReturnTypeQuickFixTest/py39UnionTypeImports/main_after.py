from typing import Union

import my
from my import X, Y


def foo(a) -> type[Union[X, Y]]:
    if a:
        return my.X
    else:
        return my.Y