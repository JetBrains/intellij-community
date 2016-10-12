class B(object):
    __slots__ = ['attr', 'b']

class C(B):
    attr = 'baz'
    __slots__ = ['foo', 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
<warning descr="'C' object attribute 'attr' is read-only">c.attr</warning> = 'spam'
print(c.attr)