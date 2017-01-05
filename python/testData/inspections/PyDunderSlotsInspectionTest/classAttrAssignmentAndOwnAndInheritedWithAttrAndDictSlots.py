class B(object):
    __slots__ = ['attr', 'b', '__dict__']

class C(B):
    attr = 'baz'
    __slots__ = ['foo', 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)