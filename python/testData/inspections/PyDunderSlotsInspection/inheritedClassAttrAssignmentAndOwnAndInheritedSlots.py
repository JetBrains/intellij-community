class B(object):
    attr = 'baz'
    __slots__ = ['f', 'b']

class C(B):
    __slots__ = ['foo', 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
<warning descr="'C' object has no attribute 'attr'">c.attr</warning> = 'spam'
print(c.attr)