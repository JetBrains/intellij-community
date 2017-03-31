class Base(object):
    __slots__ = 'bar'
    foo = 1

class Derived(Base):
    __slots__ = 'foo'