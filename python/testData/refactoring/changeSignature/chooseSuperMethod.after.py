
class B():
  def baz(self):
    print(2)

class A(object):
  def m(self):
    print(1)

class C(B, A):
  def baz(self):
    super(C, self).baz()
    print(3)
