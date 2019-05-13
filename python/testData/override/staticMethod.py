class A:
    @staticmethod
    def foo(cls):
        cls.k = 3

class B(A):
    <caret>pass
