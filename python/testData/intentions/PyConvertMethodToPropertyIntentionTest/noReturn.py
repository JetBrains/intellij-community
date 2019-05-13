class MyClass(object):
  """
  My class to show intention.
  """
  def __init__(self):
      self._x = None

  def x<caret>(self):
      print self._x


x = MyClass().x()