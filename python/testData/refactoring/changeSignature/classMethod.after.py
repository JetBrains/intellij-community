class A:
  def fo<caret>o(self, a, b):
    pass

class B(A):
  def foo(self, a, b):
    pass

class С(A):
  def foo(self, a, b):
    pass


a = A()
a.foo(1, 2)

b = B()
b.foo(2, 2)