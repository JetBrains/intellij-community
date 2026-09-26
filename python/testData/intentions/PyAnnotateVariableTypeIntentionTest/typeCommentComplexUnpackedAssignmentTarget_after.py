from typing import Literal


def func():
    ((var, _), _) = ('foo', 1), 2  # type: (([Literal['foo']], [Literal[1]]), [Literal[2]])
    var
