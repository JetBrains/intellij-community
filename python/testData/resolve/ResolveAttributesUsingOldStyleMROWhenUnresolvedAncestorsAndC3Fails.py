class X(Unresolved):
    pass


class Y(Unresolved):
    pass


class A(X, Y):
    def foo(self):
        pass


class B(Y, X):
    pass


class C(A, B):  # we don't know whether MRO is OK or not
    pass


print(C.foo)
#       <ref>
