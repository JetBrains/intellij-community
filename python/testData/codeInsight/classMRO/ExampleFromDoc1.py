class D(object):
    pass


class E(object):
    pass


class F(object):
    pass


class B(D, E):
    pass


class C(D, F):
    pass


class A(B, C):
    pass
