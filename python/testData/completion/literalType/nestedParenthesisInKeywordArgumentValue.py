from typing import Literal


def f(x: Literal[Literal[Literal[1, "-1"], "foo"], "6", None]) -> None:
    pass


f(x=((((<caret>)))))
