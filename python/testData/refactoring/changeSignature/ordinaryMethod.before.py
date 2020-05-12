class A:
  def fo<caret>o(self, a):
    pass

class B(A):
  def foo(self, a):
    pass

class С(A):
  def foo(self, a):
    pass


a = A()
a.foo(1)
A.foo(a, 1)

b = B()
b.foo(2)
B.foo(b, 2)