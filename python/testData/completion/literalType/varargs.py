from typing import Literal


def foo(*x: Literal[Literal[Literal["aa", 'bbb'], "zzz"], 5]):
    pass


foo("<caret>")
