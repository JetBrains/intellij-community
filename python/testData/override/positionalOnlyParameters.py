class A:
    def foo(self, a, b, /, c, d, *, e, f):
        pass

class B(A):
    <caret>