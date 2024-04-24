from typing import Literal


def f(x: Literal["foo", 3, "bar"]) -> None:
    pass


f('<caret>')
