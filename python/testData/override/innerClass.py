class X(object):
    def foo(self): pass
    
class Outer(object):
  class <caret>Inner(X):
    pass