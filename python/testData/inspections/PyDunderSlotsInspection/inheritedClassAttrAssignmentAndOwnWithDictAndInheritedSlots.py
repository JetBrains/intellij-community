class B(object):
    attr = 'baz'
    __slots__ = ['f', 'b']

class C(B):
    __slots__ = ['foo', 'bar', '__dict__']

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)