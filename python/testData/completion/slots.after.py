class A(object):
    __slots__ = ['foo', 'bar']

a = A()
a.bar


class B(object):
    __slots__ = ['bar']

class C(B):
    pass

C().bar


class D(object):
    pass

class E(D):
    __slots__ = ['bar']

E().bar


class F:
    __slots__ = ['baz']

F().ba