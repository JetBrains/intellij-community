class A(object):
    __slots__ = ['a', 'b']

    def __init__(self):
        self.a = None  # <- all ok here
        self.b = None  # <- all ok here


class B(A):
    __slots__ = A.__slots__

    def __init__(self):
        super(B, self).__init__()
        self.<warning descr="'B' object has no attribute 'c'">c</warning> = 'bug'


EXTERNAL_SLOTS = ['a', 'b']


class C(A):
    __slots__ = EXTERNAL_SLOTS

    def __init__(self):
        super(C, self).__init__()
        self.<warning descr="'C' object has no attribute 'c'">c</warning> = 'bug'
