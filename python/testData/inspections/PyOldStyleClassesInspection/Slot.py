class A:
    <warning descr="Old-style class contains __slots__ definition">__slots__</warning>="123"

class Base(object):
    __slots__="123"

class Derived(Base):
    __slots__="123"

class DerivedDerived(Derived):
    __slots__="123"