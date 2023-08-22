import attr

# no kw_only, no default
@attr.dataclass
class Base1:
    a: int

@attr.dataclass(kw_only=True)
class Derived11(Base1):
    b: str

@attr.s
class Derived12(Base1):
    b = attr.ib(type=str, kw_only=True)


# kw_only, no default
@attr.dataclass(kw_only=True)
class Base2:
    a: int

@attr.dataclass(kw_only=True)
class Derived21(Base2):
    b: str

@attr.s
class Derived22(Base2):
    b = attr.ib(type=str, kw_only=True)


# no kw_only, default
@attr.dataclass
class Base3:
    a: int = 1

@attr.dataclass(kw_only=True)
class Derived31(Base3):
    b: str

@attr.s
class Derived32(Base3):
    b = attr.ib(type=str, kw_only=True)


# kw_only, default
@attr.dataclass(kw_only=True)
class Base4:
    a: int = 1

@attr.dataclass(kw_only=True)
class Derived41(Base4):
    b: str

@attr.s
class Derived42(Base4):
    b = attr.ib(type=str, kw_only=True)