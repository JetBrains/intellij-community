from dataclasses import dataclass

@dataclass(kw_only=True)
class Base1:
    a: int

@dataclass
class Derived1(Base1):
    a: int

Derived1(<arg1>)

@dataclass
class Base2:
    a: int

@dataclass(kw_only=True)
class Derived2(Base2):
    a: int

Derived2(<arg2>)

@dataclass(kw_only=True)
class Base3:
    a: int

@dataclass(kw_only=True)
class Derived3(Base3):
    a: int

Derived3(<arg3>)