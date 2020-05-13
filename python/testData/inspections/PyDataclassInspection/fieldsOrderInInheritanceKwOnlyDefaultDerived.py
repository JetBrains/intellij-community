import attr

# no kw_only, no default
@attr.dataclass
class Base1:
    a: int

@attr.dataclass(kw_only=True)
class Derived1(Base1):
    b: str = "b"


# kw_only, no default
@attr.dataclass(kw_only=True)
class Base2:
    a: int

@attr.dataclass(kw_only=True)
class Derived2(Base2):
    b: str = "b"


# no kw_only, default
@attr.dataclass
class Base3:
    a: int = 1

@attr.dataclass(kw_only=True)
class Derived3(Base3):
    b: str = "b"


# kw_only, default
@attr.dataclass(kw_only=True)
class Base4:
    a: int = 1

@attr.dataclass(kw_only=True)
class Derived4(Base4):
    b: str = "b"