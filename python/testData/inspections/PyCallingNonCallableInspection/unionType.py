class A(object):
    pass
class B(object):
    pass
def foo(c):
    x = A if c else B
    y = A if c else 10
    z = A() if c else 10
    return x(), <warning descr="Member 'Literal[10]' of 'type[A] | Literal[10]' is not callable">y()</warning>, <warning descr="Members 'A | Literal[10]' of 'A | Literal[10]' are not callable">z()</warning>
