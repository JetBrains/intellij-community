class B(object):
    c1 = 0

    def f(self, x):
        self.i1 = x
        l1 = self.i1
        self.i2 = l1

    def __init__(self, x, y):
        self.i2 = x
        self.i3 = y

    @classmethod
    def g(cls, x):
        cls.c2 = cls.c1

g1 = "foo"

class C(B):
    c2 = -1

    def __init__(self, x, y):
        super(C, self).__init__(x, y)
        self.i3 = -2
        self.i4 = -3

    def h(self):
        self.i5 = self.i6

    c3 = -4

g2 = "bar"
