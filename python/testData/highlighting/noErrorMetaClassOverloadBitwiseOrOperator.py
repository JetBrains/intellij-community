class MetaA(type):
    def __or__(self, other):
        return 42


class A(metaclass=MetaA):
    pass


class B:
    pass


print(A | B)
