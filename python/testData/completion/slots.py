class A(object):
    __slots__ = ['foo', 'bar']

a = A()
a.ba<caret>


class B(object):
    __slots__ = ['bar']

class C(B):
    pass

C().ba<caret>


class D(object):
    pass

class E(D):
    __slots__ = ['bar']

E().ba<caret>


class F:
    __slots__ = ['baz']

F().ba<caret>