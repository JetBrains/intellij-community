class B(object):
    attr = 'baz'
    __slots__ = ['f', 'b', '__dict__']

class C(B):
    __slots__ = ['attr', 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)