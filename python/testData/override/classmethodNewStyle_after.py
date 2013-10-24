class A(object):
    @classmethod
    def m(cls):
        pass

class B(A):
    @classmethod
    def m(cls):
        <selection>super(B, cls).m()</selection>

