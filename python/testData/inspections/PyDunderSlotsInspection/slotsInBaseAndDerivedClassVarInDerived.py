class Base(object):
    __slots__ = 'foo'

class Derived(Base):
    __slots__ = 'bar'
    foo = 1