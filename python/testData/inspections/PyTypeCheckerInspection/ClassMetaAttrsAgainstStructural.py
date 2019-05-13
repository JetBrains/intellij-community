class B1(type):
    meta_attr = "meta_attr"


class A1(metaclass=B1):
    pass


def print_A1(a):
    print(a.__name__)
    print(a.meta_attr)


def print_unknown(a):
    print(a.unknown)


print_A1(A1)
print_unknown(<warning descr="Type 'Type[A1]' doesn't have expected attribute 'unknown'">A1</warning>)


class B2(type):
    def __init__(self, what, bases, dict):
        self.meta_attr = "meta_attr"
        super().__init__(what, bases, dict)


class A2(metaclass=B2):
    pass


def print_A2(a):
    print(a.meta_attr)


print_A2(A2())
print_unknown(<warning descr="Type 'A2' doesn't have expected attribute 'unknown'">A2()</warning>)
