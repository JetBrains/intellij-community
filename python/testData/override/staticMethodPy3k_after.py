class A:
    @staticmethod
    def m(x, y):
        pass

class B(A):
    @staticmethod
    def m(x, y):
        <selection>super().m(x, y)</selection>
