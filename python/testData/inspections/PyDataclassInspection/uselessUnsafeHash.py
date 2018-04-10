import dataclasses


@dataclasses.dataclass(<error descr="'unsafe_hash' should be false if the class defines '__hash__'">unsafe_hash=True</error>)
class A2:
    a: int = 1

    def __hash__(self):
        pass


class Base2:
    def __hash__(self):
        pass


@dataclasses.dataclass(unsafe_hash=True)
class Derived2(Base2):
    d: int = 1


@dataclasses.dataclass(<error descr="'unsafe_hash' should be false if the class defines '__hash__'">unsafe_hash=True</error>)
class A1:
    a: int = 1

    __hash__ = None


class Base1:
    __hash__ = None


@dataclasses.dataclass(unsafe_hash=True)
class Derived1(Base1):
    d: int = 1