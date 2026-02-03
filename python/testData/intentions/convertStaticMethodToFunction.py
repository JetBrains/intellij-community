class MyClass(object):
  """
  My class to show intention.
  """

  def __init__(self):
    self.a = 1

  @staticmethod
  def my_<caret>static_method():
    import code
    import time

    time.sleep(100)
    print code
