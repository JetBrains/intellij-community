# ValueError: 'attr' in __slots__ conflicts with class variable
# This is not responsibility of current inspection
class B(object):
    attr = 'baz'
    __slots__ = ['attr', 'b']

class C(B):
    __slots__ = ['foo', 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)