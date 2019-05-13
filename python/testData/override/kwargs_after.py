class A():
    def m(self, *args, **kwargs):
        pass

class B(A):
    def m(self, *args, **kwargs):
        <selection>super().m(*args, **kwargs)</selection>
