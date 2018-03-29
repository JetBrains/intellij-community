from abc import abstractmethod


class Abstract(object):

  @abstractmethod
  def foo(self, bar):
    pass


class Concrete(Abstract):
  """The docstring."""

  def foo(self, bar):
      <selection>super(Concrete, self).foo(bar)</selection>