from dataclasses import dataclass, field

@dataclass
class Base:
    a: int = field(kw_only=True)
    b: int = field(kw_only=False)
    c: int = field(kw_only=False)

@dataclass
class Derived(Base):
    c: int = field(kw_only=True)

Base(0, 0, a=0)
Base(0, 0, <warning descr="Unexpected argument">0</warning><warning descr="Parameter 'a' unfilled">)</warning>

Derived(0, a=0, c=0)
Derived(0, <warning descr="Unexpected argument">0</warning>, c=0<warning descr="Parameter 'a' unfilled">)</warning>
Derived(0, <warning descr="Unexpected argument">0</warning>, a=0<warning descr="Parameter 'c' unfilled">)</warning>