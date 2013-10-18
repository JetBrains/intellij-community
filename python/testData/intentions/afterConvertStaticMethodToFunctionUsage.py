class MyClass(object):
  """
  My class to show intention.
  """

  def __init__(self):
    self.a = 1


def my_static_method():
  import code
  import time

  time.sleep(100)
  print code


my_static_method()