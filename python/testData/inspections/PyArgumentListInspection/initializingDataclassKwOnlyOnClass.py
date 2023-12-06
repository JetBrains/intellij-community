from dataclasses import dataclass

@dataclass(kw_only=True)
class Base:
    a: int
    b: int

@dataclass
class Derived(Base):
    c: int

Base(<warning descr="Unexpected argument">0</warning>, <warning descr="Unexpected argument">0</warning><warning descr="Parameter 'a' unfilled"><warning descr="Parameter 'b' unfilled">)</warning></warning>
Base(<warning descr="Unexpected argument">0</warning>, a=0<warning descr="Parameter 'b' unfilled">)</warning>
Base(a=0, b=0)

Derived(0, <warning descr="Unexpected argument">0</warning>, <warning descr="Unexpected argument">0</warning><warning descr="Parameter 'a' unfilled"><warning descr="Parameter 'b' unfilled">)</warning></warning>
Derived(0, a=0, b=0)