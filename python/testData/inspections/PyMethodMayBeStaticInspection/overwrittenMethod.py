class A:
  def <weak_warning descr="Method 'f' may be 'static'">f</weak_warning>(self, a):
    print "A"

class B(A):
  def f(self, a):
    print "B"