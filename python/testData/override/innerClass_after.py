class X(object):
    def foo(self):
        pass

class A:
    class Inner(X):
        def foo(self):
            <selection>super(A.Inner, self).foo()</selection>

    def doStuff(self, foo=True): pass

class B(A):
    def otherMethod(self, foo, bar):
        print foo, bar
