# copied doc of inherited method
class A:
  def foo(self):
    "Doc from A.foo."
    pass

class B(A):
  def foo(self):
    return None

b = B()
b.<the_ref>foo()
