from typing import Literal


def func():
    var: [Literal['spam']]
    var, _ = 'spam', 42
    var
