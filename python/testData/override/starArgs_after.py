class A:
    def f2(self, *args, a = 1):
        pass

class B(A):
    def f2(self, *args, a=1):
        <selection>super().f2(*args, a=a)</selection>
