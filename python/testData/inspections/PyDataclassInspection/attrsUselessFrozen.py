import attr


@attr.dataclass(frozen=True)
class A4:
    a: int = 1

    def <warning descr="'__setattr__' is ignored if the class already defines 'frozen' parameter">__setattr__</warning>(self, key, value):
        pass

# A4(1).b = 2


class Base4:
    def __setattr__(self, key, value):
        pass


@attr.dataclass(frozen=True)
class Derived4(Base4):
    d: int = 1

# Derived4(1).b = 2


@attr.dataclass(frozen=True)
class A2:
    a: int = 1

    def <warning descr="'__delattr__' is ignored if the class already defines 'frozen' parameter">__delattr__</warning>(self, key):
        pass

# del A2(1).a


class Base2:
    def __delattr__(self, key):
        pass


@attr.dataclass(frozen=True)
class Derived2(Base2):
    d: int = 1

# del Derived2(1).d