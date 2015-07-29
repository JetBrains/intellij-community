class A:
    def __init__(self, a, b=2, c=3):
        self.a = a


class B(A):
    def __init__(self, a, c):
        A.__init__(self, a, c)
