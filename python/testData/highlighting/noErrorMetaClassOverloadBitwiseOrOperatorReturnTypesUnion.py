import types


class MetaA(type):
    def __or__(self, other) -> types.Union:
        return types.Union()


class A(metaclass=MetaA):
    pass


class B:
    pass


print(A | B)
