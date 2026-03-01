class Point:
    __match_args__ = ("x", "y")
    def __init__(self, x, y):
        self.x = x
        self.y = y

def f(p):
    match p:
        case Point(a, b, <warning descr="Too many positional patterns, expected 2"><caret>c</warning>):
            pass
