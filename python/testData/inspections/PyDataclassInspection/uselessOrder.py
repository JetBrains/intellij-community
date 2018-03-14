import dataclasses


@dataclasses.dataclass(<error descr="'order' should be false if the class defines one of order methods">order=True</error>)
class A1:
    a: int = 1

    def __le__(self, other):
        return "le1"


class Base1:
    def __le__(self, other):
        return "le2"


@dataclasses.dataclass(order=True)
class Derived1(Base1):
    d: int = 1


@dataclasses.dataclass(<error descr="'order' should be false if the class defines one of order methods">order=True</error>)
class A2:
    a: int = 1

    def __lt__(self, other):
        return "lt1"


class Base2:
    def __lt__(self, other):
        return "lt2"


@dataclasses.dataclass(order=True)
class Derived2(Base2):
    d: int = 1


@dataclasses.dataclass(<error descr="'order' should be false if the class defines one of order methods">order=True</error>)
class A3:
    a: int = 1

    def __gt__(self, other):
        return "gt1"


class Base3:
    def __gt__(self, other):
        return "gt2"


@dataclasses.dataclass(order=True)
class Derived3(Base3):
    d: int = 1


@dataclasses.dataclass(<error descr="'order' should be false if the class defines one of order methods">order=True</error>)
class A4:
    a: int = 1

    def __ge__(self, other):
        return "ge1"


class Base4:
    def __ge__(self, other):
        return "ge2"


@dataclasses.dataclass(order=True)
class Derived4(Base4):
    d: int = 1