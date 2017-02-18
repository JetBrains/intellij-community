class B(object):
    __slots__ = ['f', 'b']

class C(B):
    attr = 'baz'
    __slots__ = [<warning descr="'attr' in __slots__ conflicts with class variable">'attr'</warning>, 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)