class M1(type):
    pass


class M2(M1):
    def foo(cls):
        pass

    def __getitem__(self, item):
        pass


class C1(metaclass=M1):
    pass


class C2(metaclass=M2):
    pass


class D2(C1, C2):
    pass


class E(D2):
    pass


print(E.foo())
print(E[10])
print(E.<warning descr="Unresolved attribute reference 'bar' for class 'E'">bar</warning>())
