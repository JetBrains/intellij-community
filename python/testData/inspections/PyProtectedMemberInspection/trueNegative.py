__author__ = 'ktisha'

class A:
  def __init__(self):
    self._a = 1

  def foo(self):
    self.b= 1

class B(A):
  def __init__(self):
    A.__init__(self)
    self.b = self._a


