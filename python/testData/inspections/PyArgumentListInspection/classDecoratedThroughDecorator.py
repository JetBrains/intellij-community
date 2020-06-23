import abcd

@abcd.s
class A(object):
    a = abcd.ib()

A(a="test")