class A:
    def __init__(self, c, a = 5):
        pass

class B(A):
  def <caret><warning descr="Call to __init__ of super class is missed">__init__</warning>(self, r, b = 6):
    """docstring"""
    print "Constructor B was called"
