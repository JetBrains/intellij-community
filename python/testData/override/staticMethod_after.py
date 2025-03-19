class A:
    @staticmethod
    def foo(cls):
        cls.k = 3

class B(A):
    @staticmethod
    def foo(cls):
        <selection>super().foo(cls)</selection>
