from typing import LiteralString

def expectsStr(x: str):
    expectsLiteralString(x)


def expectsLiteralString(x: LiteralString):
    expectsStr(x)
