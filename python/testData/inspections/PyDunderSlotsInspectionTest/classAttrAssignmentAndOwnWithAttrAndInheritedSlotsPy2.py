class B(object):
    __slots__ = ['f', 'b']

class C(B):
    attr = 'baz'
    __slots__ = ['attr', 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
<warning descr="'C' object attribute 'attr' is read-only">c.attr</warning> = 'spam'
print(c.attr)