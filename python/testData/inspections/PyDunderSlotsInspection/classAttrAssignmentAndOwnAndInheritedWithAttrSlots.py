class B(object):
    __slots__ = ['attr', 'b']

class C(B):
    attr = 'baz'
    __slots__ = ['foo', 'bar']

c = C()
<warning descr="'C' object has no attribute 'attr'">c.attr</warning> = 'spam'
B.attr = 'baz'
print(c.attr) # outputs 'spam'

C.attr = 'spam' # this shadows descriptor c.attr rendering the assignment c.attr = 'spam' invalid
print(C.attr)