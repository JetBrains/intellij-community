class A:
    def __init__(self, *, a):
        pass


class B(A):
    def __init__(self, a):
        super().__init__(a=a)