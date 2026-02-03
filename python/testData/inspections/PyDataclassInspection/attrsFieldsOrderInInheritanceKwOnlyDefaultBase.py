import attr

@attr.dataclass(kw_only=True)
class Base1:
    a: int = 1


# no kw_only, no default
@attr.dataclass
class Derived11(Base1):
    b: str


# kw_only, no default
@attr.dataclass(kw_only=True)
class Derived12(Base1):
    b: str


# no kw_only, default
@attr.dataclass
class Derived13(Base1):
    b: str = "b"


# kw_only, default
@attr.dataclass(kw_only=True)
class Derived14(Base1):
    b: str = "b"


@attr.s
class Base2:
    a = attr.ib(type=int, kw_only=True)


# no kw_only, no default
@attr.dataclass
class Derived21(Base2):
    b: str


# kw_only, no default
@attr.dataclass(kw_only=True)
class Derived22(Base2):
    b: str


# no kw_only, default
@attr.dataclass
class Derived23(Base2):
    b: str = "b"


# kw_only, default
@attr.dataclass(kw_only=True)
class Derived24(Base2):
    b: str = "b"