import abcd

@abcd.s
class A(object):
    a = abcd.ib()

A(<warning descr="Unexpected argument">a="test"</warning>)