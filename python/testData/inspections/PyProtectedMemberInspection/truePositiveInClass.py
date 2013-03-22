__author__ = 'ktisha'

class A:
  def __init__(self):
    self._a = 1

  def foo(self):
    self.b= 1

class B:
  def __init__(self):
    <weak_warning descr="Access to a protected member _a of a class">A()._a</weak_warning>
