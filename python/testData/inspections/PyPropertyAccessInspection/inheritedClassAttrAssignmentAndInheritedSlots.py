class B(object):
    attr = 'baz'
    __slots__ = ['foo', 'bar']


class C(B):
    pass

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)