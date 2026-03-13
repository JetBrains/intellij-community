class B:
    def foo(self, arg1, arg2=None, arg3=None, arg4=None):
        pass


class C(B):
    # can accept foo(1, 2, 3) but not foo(1, 2, 3, 4), unlike B.foo(...)
    def foo<warning descr="Signature of method 'C.foo()' does not match signature of the base method in class 'B'">(self, arg1, arg2=None, arg3=None, **kwargs)</warning>:
        pass
