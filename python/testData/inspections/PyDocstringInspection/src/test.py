class A:
    pass

def foo():
  pass


class B:
    """"""
    pass

def bar():
  """"""
  pass


class C:
  @property
  def x(self):
      return 42

  @x.setter
  def x(self, value):
      pass

  @x.deleter
  def x(self):
      pass