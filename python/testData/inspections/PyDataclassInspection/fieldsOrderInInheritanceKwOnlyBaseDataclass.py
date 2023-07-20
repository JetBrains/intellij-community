from dataclasses import dataclass, field

# kw_only, no default
@dataclass(kw_only=True)
class Base1:
    a: int

@dataclass(kw_only=True)
class Derived11(Base1):
    b: str = "b"

@dataclass
class Derived12(Base1):
    b: str = field(default="b", kw_only=True)



# kw_only, default
@dataclass(kw_only=True)
class Base2:
    a: int = field(default=5)

@dataclass(kw_only=True)
class Derived21(Base2):
    b: str = "b"

@dataclass
class Derived22(Base2):
    b: str = field(default="b", kw_only=True)