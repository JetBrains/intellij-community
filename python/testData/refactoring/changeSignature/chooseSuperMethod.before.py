
class B():
  def m(self):
    print(2)

class A(object):
  def m(self):
    print(1)

class C(B, A):
  def m<caret>(self):
    super(C, self).m()
    print(3)
