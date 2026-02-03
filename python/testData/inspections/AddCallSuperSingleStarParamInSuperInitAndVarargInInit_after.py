class A:
    def __init__(self, *, kw_only):
        pass

class B(A):
    def __init__(self, *args, another_kw_only, kw_only):
        super().__init__(kw_only=kw_only)