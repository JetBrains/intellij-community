from typing import Literal


def f(x: Literal[Literal[Literal[1, 2, 3], "foo"], 5, None]) -> None:
    pass


f(<caret>)
