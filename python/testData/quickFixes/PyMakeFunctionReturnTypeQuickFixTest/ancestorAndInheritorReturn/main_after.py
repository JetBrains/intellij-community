import my
from my import X, Y


def foo(a) -> type[X | Y]:
    if a:
        return my.X<caret>
    else:
        return my.Y