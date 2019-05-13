import attr


@attr.dataclass(hash=True)
class A2:
    a: int = 1

    def <warning descr="'__hash__' is ignored if the class already defines 'hash' parameter">__hash__</warning>(self):
        pass

print(hash(A2()))


class Base2:
    def __hash__(self):
        pass


@attr.dataclass(hash=True)
class Derived2(Base2):
    d: int = 1

print(hash(Derived2()))


@attr.dataclass(hash=True)
class A1:
    a: int = 1

    <warning descr="'__hash__' is ignored if the class already defines 'hash' parameter">__hash__</warning> = None

print(hash(A1()))


class Base1:
    __hash__ = None


@attr.dataclass(hash=True)
class Derived1(Base1):
    d: int = 1

print(hash(Derived1()))


@attr.s(frozen=True)
class A3:

    def <warning descr="'__hash__' is ignored if the class already defines 'cmp' and 'frozen' parameters">__hash__</warning>(self):
        pass

print(hash(A3()))
