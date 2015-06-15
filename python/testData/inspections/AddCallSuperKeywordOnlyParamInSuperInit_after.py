class A:
    def __init__(self, a, b=1, *args, kw_only):
        pass


class B(A):
    def __init__(self, c, a, *args, kw_only):
        super().__init__(a, *args, kw_only=kw_only)