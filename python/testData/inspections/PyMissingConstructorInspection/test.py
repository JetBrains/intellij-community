import random

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


class A2:
  def __init__(self):
    print("A2 __init__")


class B2(A2):
  def __init__(self):
    A2.__init__(self)
    print("Constructor B2 was called")

class C2(B2):
  def <warning descr="Call to constructor of super class is missed">__init__</warning>(self):
    print("Constructor C2 was called")

class D2(A2):
  def __init__(self):
    if True:
      A2.__init__(self)
    print("Constructor D2 was called")

#PY-3238
class Kl:
    pass

class Kl2(Kl):
    def __init__(self):
        pass

#PY-3313
class A3(object):
    def __init__(self):
        print ("Constructor A3 was called")

class B3(A3):
    pass

class C3(B3):
    def <warning descr="Call to constructor of super class is missed">__init__</warning>(self):
        print ("Constructor C3 was called")

#PY-3395
class Over(Over):
  def __init__(self):
    pass

#PY-4038
class Child(Base):
    def __init__(self):
        super(self.__class__, self).__init__()