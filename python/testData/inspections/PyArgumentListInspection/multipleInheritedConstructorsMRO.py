class A(object):
    pass


class B(object):
    def __init__(self, *args):
        pass


class C(B):
    pass


class D(A, C):
    pass


D(42)
