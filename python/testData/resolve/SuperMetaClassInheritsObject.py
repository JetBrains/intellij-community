__metaclass__ = type

class A:
    def foo(self):
        print "foo"

class B(A):
    def foo(self):
        super(B, self).foo()


B().__getattribute__
#   <ref>