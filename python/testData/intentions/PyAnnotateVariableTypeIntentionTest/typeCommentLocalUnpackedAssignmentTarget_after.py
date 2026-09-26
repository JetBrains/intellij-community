from typing import Literal


def func():
    var, _ = 'spam', 42  # type: ([Literal['spam']], [Literal[42]])
    var
