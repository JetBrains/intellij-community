class B1:
    def foo(self, *args, **kwargs):
        raise NotImplementedError()


class C1(B1):
    def foo(self): # pass
        pass


class C2(B1):
    def foo(self, arg1): # pass
        pass


class B3:
    def foo(self, arg1, *args, **kwargs):
        raise NotImplementedError()


class C3(B3):
    def foo<warning descr="Signature of method 'C3.foo()' does not match signature of base method in class 'B3'">(self, arg1, arg2=None)</warning>: # fail
        pass
