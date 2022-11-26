from typing import Literal


class MyClassWithVeryVeryVeryLongName:
    pass


class MyClassWithVeryVeryVeryLongNameNumberTwo:
    pass


def foo(parameter: MyClassWithVeryVeryVeryLongName | MyClassWithVeryVeryVeryLongNameNumberTwo | int, short_param: str):
    pass


foo(1, <arg1>)


Upper = Literal["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"]
Lower = Literal["a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"]


def g(p, u: Upper, l: Lower):
    pass


g(42, <arg2>)
