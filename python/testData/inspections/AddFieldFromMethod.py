class A:
  def __init__(self):
    self.x = 1

  def foo(self):
    a = self.<warning descr="Unresolved attribute reference 'y' for class 'A'">y</warning>
