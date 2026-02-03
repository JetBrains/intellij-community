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


class Parent:
    """
    Parent class doc
    """
    def foo(self):
        """
        Parent foo doc
        """
        pass

class Child(Parent):
    def foo(self):
        pass
