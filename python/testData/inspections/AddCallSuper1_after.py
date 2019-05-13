class A:
    def __init__(self, c, a = 5):
        pass

class B(A):
  def __init__(self, r, c, b=6):
    """docstring"""
    A.__init__(self, c)
    print "Constructor B was called"
