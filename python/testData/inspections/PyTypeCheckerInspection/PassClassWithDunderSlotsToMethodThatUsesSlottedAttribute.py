class A(object):
    __slots__ = 'x', 'y'


def copy_values(a):
    print(a.x)


copy_values(A())