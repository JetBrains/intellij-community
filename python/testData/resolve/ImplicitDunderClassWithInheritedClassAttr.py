class A(object):
    __class__ = int

class B(A):
    def foo(self):
        return __class__
#                <ref>