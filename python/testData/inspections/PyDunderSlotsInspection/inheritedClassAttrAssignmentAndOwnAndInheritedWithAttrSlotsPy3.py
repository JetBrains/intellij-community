class B(object):
    attr = 'baz'
    __slots__ = [<warning descr="'attr' in __slots__ conflicts with class variable">'attr'</warning>, 'b']

class C(B):
    __slots__ = ['foo', 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
<warning descr="'C' object attribute 'attr' is read-only">c.attr</warning> = 'spam'
print(c.attr)