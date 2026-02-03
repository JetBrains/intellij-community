class A(object):
    def foo(self):
        return 0


class B(object):
    def bar(self):
        return 0


class MyMeta(type):
    def mro(cls):
        return A, B


class C(B):
    __metaclass__ = MyMeta


c = C()
print(c.foo().lower())  # pass
print(c.bar().<warning descr="Unresolved attribute reference 'lower' for class 'int'">lower</warning>())
