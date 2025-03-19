from typing import Literal


def f(x, y: Literal[Literal["abb"], "bac"]):
    pass


f(1, ((("<caret>"))))