class MyClass(object):
  """
  My class to show intention.
  """
  def __init__(self):
      self._x = None

  @property
  def x(self):
      return self._x


x = MyClass().x