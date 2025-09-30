from typing import Literal


def f(x: Literal["upper", "lower"]):
    pass

y = ""
f(y.up<caret>)