class B(object):
    __slots__ = ['foo']

b = B()
b.<warning descr="'B' object has no attribute 'bar'">bar</warning> = 1