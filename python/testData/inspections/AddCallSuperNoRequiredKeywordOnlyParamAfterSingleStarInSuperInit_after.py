class A:
    def __init__(self, a, b=1, *, kw_only=2):
        pass


class B(A):
    def __init__(self, a):
        super().__init__(a)