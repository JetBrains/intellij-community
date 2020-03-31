class A:
    def __init__(self, a, /, b, *args, c, **kwargs):
        pass

class B(A):
    def __init__(self, a, /, b, *args, c, **kwargs):
        super().__init__(a, b, *args, c=c, **kwargs)