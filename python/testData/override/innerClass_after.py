class X(object):
    def foo(self): pass

class Outer(object):
  class Inner(X):
    def foo(self):
        super(Outer.Inner, self).foo()

    