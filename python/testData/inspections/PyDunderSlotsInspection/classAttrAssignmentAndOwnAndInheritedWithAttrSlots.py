class B(object):
    __slots__ = ['attr', 'b']

class C(B):
    attr = 'baz'
    __slots__ = ['foo', 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
<warning descr="'C' object has no attribute 'attr'">c.attr</warning> = 'spam'
print(c.attr)