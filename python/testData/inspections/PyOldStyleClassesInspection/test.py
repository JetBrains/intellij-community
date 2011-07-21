class A:
    <warning descr="Old-style class contains __slots__ definition">__slots__</warning>="123"
    <warning descr="Old-style class contains __getattribute__ definition">def __getattribute__(self):
        pass</warning>

class Base(object):
    __slots__="123"

class Derived(Base):
    __slots__="123"

class DerivedDerived(Derived):
    __slots__="123"

class AA:
  def __init__(self):
    <warning descr="Old-style class contains call for super method">super</warning>(AA, self)

class D:
  def __init__(self):
    <warning descr="Old-style class contains call for super method">super</warning>(self.__class__, self).__init__()

class B(object):
  def __init__(self):
    super(B, self)

def super(a, b):
  pass

class C:
  def __init__(self):
    super(C, self)