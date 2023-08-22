class A(object):
    def m(self):
        pass

class B(A):
    def m(self):
        <selection>super(B, self).m()</selection>

