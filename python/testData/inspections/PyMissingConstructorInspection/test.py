import random

class R(random.WichmannHill):
    def <warning descr="Call to constructor of super class is missed">__init__</warning>(self):
        pass
#PY-3278
class R1(random.WichmannHill):
    def __init__(self):
        random.WichmannHill.__init__(self)

class A(object):
  def __init__(self):
    pass

class AA(A):
  def __init__(self):
    A.__init__(self)
    print("Constructor AA was called")

class B(A):
  def <warning descr="Call to constructor of super class is missed">__init__</warning>(self):
    print("Constructor B was called")

class C(B):
  def __init__(self):
    super(C, self).__init__()
    print("Constructor C was called")


class A:
  def __init__(self):
    print("A __init__")


class B(A):
  def __init__(self):
    A.__init__(self)
    print("Constructor B was called")

class C(B):
  def <warning descr="Call to constructor of super class is missed">__init__</warning>(self):
    print("Constructor C was called")

class D(A):
  def __init__(self):
    if True:
      A.__init__(self)
    print("Constructor D was called")