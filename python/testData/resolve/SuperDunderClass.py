class A(object):
    def foo(self):
        print "foo"

class B(A):
    def foo(self):
        super(self.__class__, self).foo()
#                                     <ref>

B().foo()
