class B:
    def foo(self, x=1):
        pass


class C(B):
    # can accept foo(x=1) but not foo(1)
    def foo<warning descr="Signature of method 'C.foo()' does not match signature of the base method in class 'B'">(self, **kwargs)</warning>:
        pass
