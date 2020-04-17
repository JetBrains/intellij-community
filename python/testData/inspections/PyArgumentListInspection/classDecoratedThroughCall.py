import abcd

class A(object):
    a = abcd.ib()

A = abcd.s(A)

A(a="test")