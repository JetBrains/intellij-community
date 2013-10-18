class A:
  def f(self, a):
    print "A"

class B(A):
  def f(self, a):
    print self.b