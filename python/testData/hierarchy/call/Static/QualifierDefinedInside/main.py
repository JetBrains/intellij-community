class A:
    def __init__(self):
        ...

    def foo(self, x):
        return x


class B(A):
    def foo(self, x):
        return D()


class D(A):
    def f<caret>oo(self, x):
        b = B()
        b.foo(x)
        return A()


class C(A):
    def foo(self, x):
        y = D()
        y.foo(x)
        return self