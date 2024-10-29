"""
Tests the synthesis of the __hash__ method in a dataclass.
"""

from dataclasses import dataclass
from typing import Hashable


@dataclass
class DC1:
    a: int


# This should generate an error because DC1 isn't hashable.
v1: Hashable = DC1(0)  # E


@dataclass(eq=True, frozen=True)
class DC2:
    a: int


v2: Hashable = DC2(0)


@dataclass(eq=True)
class DC3:
    a: int


# This should generate an error because DC3 isn't hashable.
v3: Hashable = DC3(0)  # E


@dataclass(frozen=True)
class DC4:
    a: int


v4: Hashable = DC4(0)


@dataclass(eq=True, unsafe_hash=True)
class DC5:
    a: int


v5: Hashable = DC5(0)


@dataclass(eq=True)
class DC6:
    a: int

    def __hash__(self) -> int:
        return 0


v6: Hashable = DC6(0)


@dataclass(frozen=True)
class DC7:
    a: int

    def __eq__(self, other) -> bool:
        return self.a == other.a


v7: Hashable = DC7(0)
