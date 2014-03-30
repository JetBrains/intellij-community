class A(object):
  __A = 1

class B(A):
  def f(self):
    self._<ref>_A # must fail
