"""
Tests the synthesis of the __hash__ method in a dataclass.
"""

from dataclasses import dataclass
from typing import Callable, Hashable, assert_type


@dataclass
class DC1:
    a: int


assert_type(DC1.__hash__, None)

# These should generate errors because DC1 isn't hashable.
DC1(0).__hash__()  # E
v1: Hashable = DC1(0)  # E


@dataclass(eq=True, frozen=True)
class DC2:
    a: int


dc2_hash: Callable[..., int] = DC2.__hash__  # OK
DC2(0).__hash__()  # OK
v2: Hashable = DC2(0)  # OK


@dataclass(eq=True)
class DC3:
    a: int


assert_type(DC3.__hash__, None)

# These should generate errors because DC3 isn't hashable.
DC3(0).__hash__()  # E
v3: Hashable = DC3(0)  # E


@dataclass(frozen=True)
class DC4:
    a: int


dc4_hash: Callable[..., int] = DC4.__hash__  # OK
DC4(0).__hash__()  # OK
v4: Hashable = DC4(0)  # OK


@dataclass(eq=True, unsafe_hash=True)
class DC5:
    a: int


dc5_hash: Callable[..., int] = DC5.__hash__  # OK
DC5(0).__hash__()  # OK
v5: Hashable = DC5(0)  # OK


@dataclass(eq=True)
class DC6:
    a: int

    def __hash__(self) -> int:
        return 0


dc6_hash: Callable[..., int] = DC6.__hash__  # OK
DC6(0).__hash__()  # OK
v6: Hashable = DC6(0)  # OK


@dataclass(frozen=True)
class DC7:
    a: int

    def __eq__(self, other) -> bool:
        return self.a == other.a


dc7_hash: Callable[..., int] = DC7.__hash__  # OK
DC7(0).__hash__()  # OK
v7: Hashable = DC7(0)  # OK
