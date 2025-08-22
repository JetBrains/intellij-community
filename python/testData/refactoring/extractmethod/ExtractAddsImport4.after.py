from enum import Enum

class Color(Enum):
    RED = 1
    GREEN = 2
    BLUE = 3

def f(color):
    if color == Color.RED:
        body(color)


def body(color_new):
    1
    color_new