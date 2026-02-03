class B(object):
    attr = 'baz'
    __slots__ = ['f', 'b']

class C(B):
    __slots__ = ['attr', 'bar']

c = C()
c.attr = 'spam'
B.attr = 'baz'
print(c.attr) # outputs 'spam'

C.attr = 'spam' # this shadows descriptor c.attr rendering the assignment c.attr = 'spam' invalid
print(C.attr)