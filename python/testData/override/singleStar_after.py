class A:
    def f1(self, *, a = 1):
        pass

class B(A):
    def f1(self, *, a=1):
        <selection>super().f1(a=a)</selection>
