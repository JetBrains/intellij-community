class A(object):
    __doc__ = "abc"

class B(A):
    def foo(self):
        return __doc__
#                <ref>