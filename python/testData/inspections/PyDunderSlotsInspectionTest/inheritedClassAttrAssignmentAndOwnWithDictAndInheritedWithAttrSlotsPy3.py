class B(object):
    attr = 'baz'
    __slots__ = ['f', <warning descr="'attr' in __slots__ conflicts with class variable">'attr'</warning>]

class C(B):
    __slots__ = ['foo', 'bar', '__dict__']

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)