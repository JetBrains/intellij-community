class X(<error descr="Unresolved reference 'Unresolved'">Unresolved</error>):
    pass


class Y(<error descr="Unresolved reference 'Unresolved'">Unresolved</error>):
    pass


class A(X, Y):
    def foo(self):
        pass


class B(Y, X):
    pass


class C(A, B):  # we don't know whether MRO is OK or not
    pass


print(C.foo)  # pass
