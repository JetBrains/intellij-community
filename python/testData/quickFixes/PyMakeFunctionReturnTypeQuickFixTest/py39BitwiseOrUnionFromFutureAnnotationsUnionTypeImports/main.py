from __future__ import annotations

import my


def foo(a) -> my.X:
    if a:
        return <warning descr="Expected type 'X', got 'Type[X]' instead">my.X<caret></warning>
    else:
        return <warning descr="Expected type 'X', got 'Type[Y]' instead">my.Y</warning>