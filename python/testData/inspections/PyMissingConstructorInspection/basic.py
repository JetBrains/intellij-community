class A(object):
  def __init__(self):
    pass

class AA(A):
  def __init__(self):
    A.__init__(self)
    print("Constructor AA was called")

class B(A):
  def <warning descr="Call to __init__ of super class is missed">__init__</warning>(self):
    print("Constructor B was called")

class C(B):
  def __init__(self):
    super(C, self).__init__()
    print("Constructor C was called")


class A2:
  def __init__(self):
    print("A2 __init__")


class B2(A2):
  def __init__(self):
    A2.__init__(self)
    print("Constructor B2 was called")

class C2(B2):
  def <warning descr="Call to __init__ of super class is missed">__init__</warning>(self):
    print("Constructor C2 was called")

class D2(A2):
  def __init__(self):
    if True:
      A2.__init__(self)
    print("Constructor D2 was called")
