from dataclasses import dataclass, KW_ONLY
from typing import NamedTuple


@dataclass
class A1:
    bar1: int = 1   # no error
    _: KW_ONLY
    bar2: int


@dataclass
class Base:
    x: int = 0


@dataclass
class Child1(Base):
    <error descr="Non-default argument(s) follows default argument(s) defined in 'Base'">y</error>: int
    _: KW_ONLY
    z: int = 1


@dataclass
class Child2(Base):
    _: KW_ONLY
    y: int         # no error
    z: int = 1