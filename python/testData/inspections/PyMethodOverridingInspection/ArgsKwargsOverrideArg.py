class B:
    def foo(self, a):
        pass


class C1(B):
    # cannot accept foo(a=1), unlike B.foo
    def foo<warning descr="Signature of method 'C1.foo()' does not match signature of the base method in class 'B'">(self, *a)</warning>:
        pass


class C2(B):
    def foo<warning descr="Signature of method 'C2.foo()' does not match signature of the base method in class 'B'">(self)</warning>:
        pass


class C3(B):
    # cannot accept foo(1), unlike B.foo
    def foo<warning descr="Signature of method 'C3.foo()' does not match signature of the base method in class 'B'">(self, **a)</warning>:
        pass


class C4(B):
    def foo(self, a):
        pass
