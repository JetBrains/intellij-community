class M1(type):
    def foo(cls):
        pass


class M2(type):
    def bar(cls):
        pass


class C1(metaclass=M1):
    pass


class C2(metaclass=M2):
    pass


class D(C1, C2):
    pass


print(D.<warning descr="Unresolved attribute reference 'foo' for class 'D'">foo</warning>()())
print(D.<warning descr="Unresolved attribute reference 'bar' for class 'D'">bar</warning>())
