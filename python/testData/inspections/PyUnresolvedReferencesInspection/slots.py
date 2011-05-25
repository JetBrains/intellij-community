class B(object):
    __slots__ = ['foo']

b = B()
<warning descr="'B' object has no attribute 'bar'">b.bar</warning> = 1

class C(B):
    pass

c = C()
<warning descr="'C' object has no attribute 'bar'">c.bar</warning> = 1
