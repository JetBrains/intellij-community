class B:
    def foo(self, a):
        pass


class C1(B):
    def foo(self, *a):
        pass


class C2(B):
    def foo<warning descr="Signature of method 'C2.foo()' does not match signature of base method in class 'B'">(self)</warning>:
        pass


class C3(B):
    def foo(self, **a):
        pass


class C4(B):
    def foo(self, a):
        pass
