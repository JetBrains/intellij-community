class A:
    def __init__(self, a):
        pass

class B(A):
    def __init__(self, b, a, c=1, *args, kw_only):
        super().__init__(a)