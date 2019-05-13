class A(object):
    def foo(self):
        return 0


class MyMeta(type):
    def mro(cls):
        return A, B


class MyMeta2(MyMeta):
    pass


class B(object):
    __metaclass__ = MyMeta2

    def bar(self):
        return 0


class C(B):
    pass


c = C()
print(c.foo().lower())  # pass
print(c.bar().<warning descr="Unresolved attribute reference 'lower' for class 'int'">lower</warning>())
