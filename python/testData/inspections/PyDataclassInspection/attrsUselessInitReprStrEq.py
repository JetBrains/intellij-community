import attr


@attr.dataclass(repr=True, cmp=True, str=True)
class A:
    a: int = 1

    def <warning descr="'__repr__' is ignored if the class already defines 'repr' parameter">__repr__</warning>(self):
        return "repr1"

    def <warning descr="'__str__' is ignored if the class already defines 'str' parameter">__str__</warning>(self):
        return "str1"

    def <warning descr="'__eq__' is ignored if the class already defines 'cmp' parameter">__eq__</warning>(self, other):
        return "eq1"


class Base:
    def __repr__(self):
        return "repr2"

    def __str__(self):
        return "str2"

    def __eq__(self, other):
        return "eq2"


@attr.dataclass(repr=True, cmp=True, str=True)
class Derived(Base):
    d: int = 1


@attr.dataclass
class B1:
    b: int = 1

    def <warning descr="'__init__' is ignored if the class already defines 'init' parameter">__init__</warning>(self):
        print("ok")


@attr.dataclass(init=True)
class B2:
    b: int = 1

    def <warning descr="'__init__' is ignored if the class already defines 'init' parameter">__init__</warning>(self):
        print("ok")


@attr.dataclass
class C1:
    c: int = 1

    def <warning descr="'__eq__' is ignored if the class already defines 'cmp' parameter">__eq__</warning>(self, other):
        return "eq1"

print(repr(A()))
print(str(A()))
print(A() == A())

print(repr(Derived()))
print(str(Derived()))
print(Derived() == Derived())

B1()
B2()
