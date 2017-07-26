class B(object):
  def __init__<warning descr="Signature is not compatible to __new__">(self)</warning>: # error
    pass

  def __new__<warning descr="Signature is not compatible to __init__">(cls, x, y)</warning>: # error
    pass

class A1(B):
    pass

class A2(A1):
    def __new__<warning descr="Signature is not compatible to __init__">(cls, a)</warning>: # error
       pass

    
class A3(A2):
    def __new__(cls, *a): # ok
       pass

class C(object):
    def __new__(cls, *args, **kwargs):
        pass
    
class C1(C):
    def __init__(self, a): # OK
        pass


# PY-846
from seob import SeoB
class SeoA(SeoB):
  pass


import enum


# PY-24749
class Planet(enum.Enum):
    EARTH = (5.976e+24, 6.37814e6)

    def __init__(self, mass, radius): # OK
        pass


# PY-25171
class Meta(type):
    def __new__(mcls, name, bases, namespace):
        pass