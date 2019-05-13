def f(x):
    return x.foo


class C(object):
    def __getattribute__(self, item):
        pass


class D(object):
    def __getattr__(self, item):
        pass


class E(object):
    pass


f(C())
f(D())
f(<warning descr="Type 'E' doesn't have expected attribute 'foo'">E()</warning>)
