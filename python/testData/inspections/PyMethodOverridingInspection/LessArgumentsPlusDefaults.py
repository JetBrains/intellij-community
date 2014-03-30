class B:
    def foo(self, arg1, arg2=None):
        pass


class C(B):
    def foo<warning descr="Signature of method 'C.foo()' does not match signature of base method in class 'B'">(self, arg1=None)</warning>: #fail
        pass
