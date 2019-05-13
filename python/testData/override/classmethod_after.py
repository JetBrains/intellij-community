class A:
    @classmethod
    def foo(cls):
        cls.k = 3

class B(A):
    @classmethod
    def foo(cls):
        <selection>A.foo(cls)</selection>
