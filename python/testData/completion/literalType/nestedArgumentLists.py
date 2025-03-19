from typing import Literal


def expects_literal(x: Literal["foo", "bar"]) -> None:
    pass


def f(x: str) -> str:
    pass


expects_literal(f("<caret>"))