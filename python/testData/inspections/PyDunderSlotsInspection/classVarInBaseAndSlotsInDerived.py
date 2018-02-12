class Base(object):
    foo = 1

class Derived(Base):
    __slots__ = 'foo'