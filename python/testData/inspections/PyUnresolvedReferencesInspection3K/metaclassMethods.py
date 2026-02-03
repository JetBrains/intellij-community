class M(type):
    def baz(cls):
        pass


class B(object):
    def bar(self):
        pass


class C(B, metaclass=M):
    def foo(self):
        pass


C.foo()
C.bar()
C.baz()

c = C()

c.foo()
c.bar()
c.<warning descr="Unresolved attribute reference 'baz' for class 'C'">baz</warning>()
