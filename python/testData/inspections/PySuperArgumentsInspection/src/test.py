class A:
    def foo(self):
        pass

class B(A):
    def foo(self):
        pass

class C(B):
    def foo(self):
        super(A, C).foo()
        super(B, C).foo()
        super(C, C).foo()
        super(Exception, C).args