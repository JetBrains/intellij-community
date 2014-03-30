class A(object):
    pass


class B(A):
    pass


class C(A):
    pass


class D(A):
    pass


class E(B):
    pass


class F(B):
    pass


class G(C, D):
    pass


class H(E, F, G):
    pass
