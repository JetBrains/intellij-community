class A(object):
    __slots__ = ("a", "b", "c")


class B(A):
    __slots__ = ("d", "e")

    def __init__(self):
        self.a = 2
        self.d = 2