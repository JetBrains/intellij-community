class A:
    def foo(self, a, b, /, c, d, *, e, f):
        pass

class B(A):
    def foo(self, a, b, /, c, d, *, e, f):
        super().foo(a, b, c, d, e=e, f=f)