class A(object):
    def foo(self):
        print "foo"

class B(A):
    def foo(self):
        super(B, self).f<ref>oo()

B().foo()
