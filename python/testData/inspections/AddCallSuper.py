class A:
    def __init__(self, c, a = 5, *arg, **kwargs):
        pass

class B(A):
  def <caret><warning descr="Call to constructor of super class is missed">__init__</warning>(self, r, b = 6, *args, **kwargs):
    print "Constructor B was called"
