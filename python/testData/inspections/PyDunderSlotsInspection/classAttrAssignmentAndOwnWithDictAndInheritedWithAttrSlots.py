class B(object):
    __slots__ = ['attr', 'b']

class C(B):
    attr = 'baz'
    __slots__ = ['foo', 'bar', '__dict__']

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)