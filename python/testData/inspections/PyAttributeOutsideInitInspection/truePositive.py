__author__ = 'ktisha'

class A:
  def __init__(self):
    self.a = 1

  def foo(self):
    <weak_warning descr="Instance attribute b defined outside __init__">self.b</weak_warning>= 1
