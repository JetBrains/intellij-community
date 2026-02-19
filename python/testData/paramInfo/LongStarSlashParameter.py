from typing import Literal

Upper = Literal["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"]


def star_slash(a, /, b: Upper, *, c: Upper):
    pass


star_slash(1, <arg1>)

star_slash(1, b=<arg2>)

star_slash(1, b="A", <arg3>)

star_slash(1, b="A", c=<arg4>)