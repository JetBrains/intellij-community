class A:
  def foo(self): pass
  foo = staticmethod(foo)
  
A.f<caret>oo()
