class A(object):
    pass
class B(object):
    pass
def foo(c):
    x = A if c else B
    y = A if c else 10
    return x(), <warning descr="'y' is not callable">y()</warning>
