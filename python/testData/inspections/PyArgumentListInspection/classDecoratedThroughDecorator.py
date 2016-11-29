import attr

@attr.s
class A(object):
    a = attr.ib()

A(a="test")