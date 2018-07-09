class B(object):
    __slots__ = ['foo']

b = B()
b.<warning descr="'B' object has no attribute 'bar'">bar</warning> = 1


class A(object):
    __slots__ = 'foo'

a = A()
a.<warning descr="'A' object has no attribute 'bar'">bar</warning> = 1