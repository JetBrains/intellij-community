class B(object):
    __slots__ = ['foo']

class C(B):
    pass

c = C()
c.bar = 1