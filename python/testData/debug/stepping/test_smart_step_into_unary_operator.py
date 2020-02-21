class A(object):
    def __init__(self, x):
        self.x = x

    def __neg__(self):
        return A(-self.x)

    def __pos__(self):
        return A(abs(self.x))

    def __invert__(self):
        return A(~self.x)

    def __add__(self, other):
        return A(self.x + other.x)


a1 = A(1)
a2 = A(2)
a3 = A(3)


a5 = a1 + a2 + (-a3) + (+A(-4)) + (~a1)  # breakpoint
