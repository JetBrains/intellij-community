import attr

class A(object):
    a = attr.ib()

A = attr.s(A)

A(a="test")