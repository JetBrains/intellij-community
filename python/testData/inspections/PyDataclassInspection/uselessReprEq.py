import dataclasses


@dataclasses.dataclass(<warning descr="'repr' is ignored if the class already defines corresponding method">repr=True</warning>, <warning descr="'eq' is ignored if the class already defines corresponding method">eq=True</warning>)
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

print(repr(A()))
print(A() == A())

print(repr(Derived()))
print(Derived() == Derived())