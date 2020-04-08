class A(object):
    def __init__(self, x):
        self.x = x


class B(A):
    def __init__(self, x, y):
        super(B, self).__init__(x)
        self.y = y


b = B(1, 2)
