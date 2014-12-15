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
f(<warning descr="Expected type '{foo}', got 'E' instead">E()</warning>)
