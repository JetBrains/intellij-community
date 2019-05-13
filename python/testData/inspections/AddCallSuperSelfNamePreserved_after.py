class A:
    def __init__(self, x):
        self.x = x


class B(A):
    def __init__(this, y, x):
        A.__init__(this, x)
        this.y = y