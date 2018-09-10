class A(object):
    __slots__ = 'x', 'y'


class B(object):
    __slots__ = ['x']

class C(B):
    pass


class D(object):
    pass

class E(D):
    __slots__ = ['x']


class F:
    __slots__ = ['x']


def copy_values(a):
    print(a.x)


copy_values(A())
copy_values(C())
copy_values(E())
copy_values(<warning descr="Type 'D' doesn't have expected attribute 'x'">D()</warning>)