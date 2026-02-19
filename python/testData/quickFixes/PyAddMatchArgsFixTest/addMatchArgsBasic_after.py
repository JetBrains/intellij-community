class Point:
    __match_args__ = ('x', 'y')

    def __init__(self, x, y):
        self.x = x
        self.y = y

def f(p):
    match p:
        case Point(1, 2):
            pass
