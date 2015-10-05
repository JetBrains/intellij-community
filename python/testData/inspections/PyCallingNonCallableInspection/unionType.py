class A(object):
    pass
class B(object):
    pass
def foo(c):
    x = A if c else B
    y = A if c else 10
    z = A() if c else 10
    return x(), y(), <warning descr="'z' is not callable">z()</warning>
