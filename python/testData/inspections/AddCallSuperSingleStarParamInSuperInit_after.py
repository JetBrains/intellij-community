class A:
    def __init__(self, *, kw_only, optional_kw_only=None):
        pass

class B(A):
    def __init__(self, *, kw_only):
        super().__init__(kw_only=kw_only)