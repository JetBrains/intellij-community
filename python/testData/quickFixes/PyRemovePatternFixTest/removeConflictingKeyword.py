class Point:
    __match_args__ = ("x", "y")
    def __init__(self, x, y):
        self.x = x
        self.y = y

def f(p):
    match p:
        case Point(0, <warning descr="Attribute 'x' is already specified as positional pattern at position 1"><caret>x=0</warning>):
            pass
