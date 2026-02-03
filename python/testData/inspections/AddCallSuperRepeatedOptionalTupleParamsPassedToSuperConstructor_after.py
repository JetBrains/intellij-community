class A:
    def __init__(self, x, (y, z)=(1, (2, 3)), (a, b)=(1, 2)):
        pass


class B(A):
    def __init__(self, y, z, b, x):
        A.__init__(self, x, (y, z))