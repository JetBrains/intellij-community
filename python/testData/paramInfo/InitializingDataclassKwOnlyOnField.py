from dataclasses import dataclass, field

@dataclass
class Base1:
    a: int = field(kw_only=True)

@dataclass
class Derived1(Base1):
    b: int

Derived1(<arg1>)

@dataclass
class Base2:
    a: int

@dataclass
class Derived2(Base2):
    b: int = field(kw_only=True)

Derived2(<arg2>)

@dataclass
class Base3:
    a: int = field(kw_only=True)

@dataclass
class Derived3(Base3):
    b: int = field(kw_only=True)

Derived3(<arg3>)
Base3(<arg4>)

@dataclass(kw_only=True)
class Base4:
    a: int = field(kw_only=False)
    b: int

Base4(<arg5>)