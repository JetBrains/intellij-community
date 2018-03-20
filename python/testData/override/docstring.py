from abc import abstractmethod


class Abstract(object):

  @abstractmethod
  def foo(self, bar):
    pass


class Concrete(Abstract):
  """The docstring."""