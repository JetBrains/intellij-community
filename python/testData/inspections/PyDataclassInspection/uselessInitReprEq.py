import dataclasses


@dataclasses.dataclass(<warning descr="'repr' is ignored if the class already defines '__repr__' method">repr=True</warning>, <warning descr="'eq' is ignored if the class already defines '__eq__' method">eq=True</warning>)
class A:
    a: int = 1

    def __repr__(self):
        return "repr1"

    def __eq__(self, other):
        return "eq1"


class Base:
    def __repr__(self):
        return "repr2"

    def __eq__(self, other):
        return "eq2"


@dataclasses.dataclass(repr=True, eq=True)
class Derived(Base):
    d: int = 1


@dataclasses.dataclass
class B1:
    b: int = 1

    def __init__(self):
        print("ok")


@dataclasses.dataclass(<warning descr="'init' is ignored if the class already defines '__init__' method">init=True</warning>)
class B2:
    b: int = 1

    def __init__(self):
        print("ok")

print(repr(A()))
print(A() == A())

print(repr(Derived()))
print(Derived() == Derived())

B1()
B2()