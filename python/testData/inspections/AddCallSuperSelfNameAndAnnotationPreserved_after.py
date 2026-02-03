class A:
    def __init__(self, x):
        self.x = x


class B(A):
    def __init__(this: 'B', y, x):
        super().__init__(x)
        this.y = y