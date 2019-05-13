class MyClass(object):
  """
  My class to show intention.
  """
  def __init__(self):
      self._x = None

  @property
  def x<caret>(self):
      return self._x


x = MyClass().x()