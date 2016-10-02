class B(object):
    attr = 'baz'
    __slots__ = ['f', 'attr', '__dict__']

class C(B):
    __slots__ = ['foo', 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)