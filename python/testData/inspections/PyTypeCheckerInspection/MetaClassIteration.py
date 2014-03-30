class M1(type):
    def __iter__(self):
        pass


class M2(type):
    pass


class C1(object):
    __metaclass__ = M1


class C2(object):
    __metaclass__ = M2


class B1(C1):
    pass


for x in C1:
    pass


for y in <warning descr="Expected 'collections.Iterable', got 'C2' instead">C2</warning>:
    pass


for z in B1:
    pass
