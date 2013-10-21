class A(object):
    def foo(self):
        print "foo"

class B(A):
    def foo(self):
        super().foo()
#                <ref>

B().foo()
