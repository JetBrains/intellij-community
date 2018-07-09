import attr


@attr.dataclass(cmp=True)
class A1:
    a: int = 1

    def <warning descr="'__le__' is ignored if the class already defines 'cmp' parameter">__le__</warning>(self, other):
        return "le1"

print(A1(1) <= A1(2))


class Base1:
    def __le__(self, other):
        return "le2"


@attr.dataclass(cmp=True)
class Derived1(Base1):
    d: int = 1

print(Derived1(1) <= Derived1(2))


@attr.dataclass(cmp=True)
class A2:
    a: int = 1

    def <warning descr="'__lt__' is ignored if the class already defines 'cmp' parameter">__lt__</warning>(self, other):
        return "lt1"

print(A2(1) < A2(2))


class Base2:
    def __lt__(self, other):
        return "lt2"


@attr.dataclass(cmp=True)
class Derived2(Base2):
    d: int = 1

print(Derived2(1) < Derived2(2))


@attr.dataclass(cmp=True)
class A3:
    a: int = 1

    def <warning descr="'__gt__' is ignored if the class already defines 'cmp' parameter">__gt__</warning>(self, other):
        return "gt1"

print(A3(1) > A3(2))


class Base3:
    def __gt__(self, other):
        return "gt2"


@attr.dataclass(cmp=True)
class Derived3(Base3):
    d: int = 1

print(Derived3(1) > Derived3(2))


@attr.dataclass(cmp=True)
class A4:
    a: int = 1

    def <warning descr="'__ge__' is ignored if the class already defines 'cmp' parameter">__ge__</warning>(self, other):
        return "ge1"

print(A4(1) >= A4(2))


class Base4:
    def __ge__(self, other):
        return "ge2"


@attr.dataclass(cmp=True)
class Derived4(Base4):
    d: int = 1

print(Derived4(1) >= Derived4(2))