import attr

@attr.dataclass(kw_only=True)
class Base:
    a: int = 1


# no kw_only, no default
@attr.dataclass
class Derived1(Base):
    b: str


# kw_only, no default
@attr.dataclass(kw_only=True)
class Derived2(Base):
    b: str


# no kw_only, default
@attr.dataclass
class Derived3(Base):
    b: str = "b"


# kw_only, default
@attr.dataclass(kw_only=True)
class Derived4(Base):
    b: str = "b"