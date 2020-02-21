from __future__ import print_function


class A:
    def __init__(self, a):
        self.a = a

    def __add__(self, o):
        print("__add__")
        return A(self.a + o.a)

    def __sub__(self, o):
        print("__sub__")
        return A(self.a - o.a)

    def __mul__(self, o):
        print("__mul__")
        return A(self.a * o.a)

    def __truediv__(self, o):
        print("__truediv__")
        return A(self.a / o.a)

    __div__ = __truediv__  # Python 2 compatibility

    def __floordiv__(self, o):
        print("__floordiv__")
        return A(self.a // o.a)

    def __mod__(self, o):
        print("__mod__")
        return A(self.a)

    def __pow__(self, o):
        print("__pow__")
        return A(self.a ** o.a)


def identity(x):
    return x


def foo():
    return (((((A(1) + A(2) + A(3) - A(3)) * A(1)) / A(identity(2))) // A(
        1)) % 2) ** A(3)


foo()
