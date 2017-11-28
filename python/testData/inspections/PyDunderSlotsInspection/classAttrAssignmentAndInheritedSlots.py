class B(object):
    __slots__ = ['foo', 'bar']


class C(B):
    attr = 'baz'

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)