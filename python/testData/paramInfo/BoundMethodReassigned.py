class A(object):
  def foo(self, a, b):
    pass

  moo = foo

ff = A().moo

f = ff

f(<arg1>1, <arg2>2)
