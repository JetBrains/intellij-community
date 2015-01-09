class O(object):
    pass


class X(O):
    pass


class Y(O):
    pass


class A(X, Y):
    def foo(self):
        pass


class B(Y, X):
    pass


class C(A, B):  # bad MRO
    pass


print(C.foo)  # pass
