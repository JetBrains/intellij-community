class Point:
    def __init__(self, x, y):
        self.x = x
        self.y = y

def f(p):
    match p:
        case Point(<warning descr="Class Point does not support pattern matching with positional arguments"><caret>1</warning>, <warning descr="Class Point does not support pattern matching with positional arguments">2</warning>):
            pass
