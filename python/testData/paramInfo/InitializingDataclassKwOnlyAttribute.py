from dataclasses import dataclass, KW_ONLY

@dataclass
class Base:
    a: int
    qq: KW_ONLY
    b: int

Base(<arg1>)

@dataclass
class Derived1(Base):
    b: int

Derived1(<arg2>)

@dataclass
class Derived2(Base):
    qq: int

Derived2(<arg3>)

@dataclass
class Derived3(Base):
    c: int
    ww: KW_ONLY
    d: int

Derived3(<arg4>)

@dataclass
class Derived4(Base):
    ww: KW_ONLY
    qq: int

Derived4(<arg5>)

@dataclass
class Base2:
    a: str

@dataclass
class Derived5(Base2):
    a: KW_ONLY

Derived5(<arg6>)
