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