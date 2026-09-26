class B1:
    def foo(self, a, b):
        pass


class C1(B1):
    # cannot accept foo(a=1, b=2), unlike B1.foo(...)
    def foo<warning descr="Signature of method 'C1.foo()' does not match signature of the base method in class 'B1'">(self, *b)</warning>:
        pass


class B2:
    def foo(self, **kwargs):
        pass


class C2(B2):
    # cannot accept e.g. foo(a=1), unlike B2.foo(...)
    def foo<warning descr="Signature of method 'C2.foo()' does not match signature of the base method in class 'B2'">(self)</warning>:
        pass


class B3:
    def foo(self, *args):
        pass


class C3(B3):
    # cannot accept e.g. foo(1), unlike B3.foo(...)
    def foo<warning descr="Signature of method 'C3.foo()' does not match signature of the base method in class 'B3'">(self)</warning>:
        pass
