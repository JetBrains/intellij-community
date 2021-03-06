class Foo:
    def __init__(self, x, y, z):
        self.x = x
        self.y = y
        self.z = z


def foo():
    x = 1
    y = 2
    z = 3
    return Foo(x, y, z)<caret>