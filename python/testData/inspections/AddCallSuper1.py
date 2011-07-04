class A:
    def __init__(self, c, a = 5):
        pass

class B(A):
  def <caret><warning descr="Call to constructor of super class is missed">__init__</warning>(self, r, b = 6):
    print "Constructor B was called"
