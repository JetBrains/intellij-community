class B:
    def foo(self):
        pass


class C(B):
    def foo<warning descr="Signature of method 'C.foo()' does not match signature of base method in class 'B'">(self, p1, **kwargs)</warning>:  #fail
        pass

