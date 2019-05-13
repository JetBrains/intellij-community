class A(object):
    __sizeof__ = 4

class B(A):
    def foo(self):
        return __sizeof__
#                <ref>