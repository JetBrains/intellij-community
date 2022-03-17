from typing import LiteralString

def expectsStr(x: str):
    expectsLiteralString(<warning descr="Expected type 'LiteralString', got 'str' instead">x</warning>)


def expectsLiteralString(x: LiteralString):
    expectsStr(x)
