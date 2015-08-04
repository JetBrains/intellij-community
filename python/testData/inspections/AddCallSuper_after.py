class A:
    def __init__(self, c, a = 5, *arg, **kwargs):
        pass

class B(A):
  def __init__(self, r, c, b=6, *args, **kwargs):
    A.__init__(self, c, *args, **kwargs)
    print "Constructor B was called"
