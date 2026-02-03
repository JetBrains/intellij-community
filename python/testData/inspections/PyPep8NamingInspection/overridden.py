class A:
  def <weak_warning descr="Function name should be lowercase">fooBar</weak_warning>(self): pass

class B(A):
  def fooBar(self): pass