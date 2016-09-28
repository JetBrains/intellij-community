class B(object):
    __slots__ = ['f', 'b']

# ValueError: 'attr' in __slots__ conflicts with class variable
# This is not responsibility of current inspection
class C(B):
    attr = 'baz'
    __slots__ = ['attr', 'bar']

C.attr = 'spam'
print(C.attr)

c = C()
c.attr = 'spam'
print(c.attr)