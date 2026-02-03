"""
Tests the match_args feature of dataclass added in Python 3.10.
"""

# Specification: https://docs.python.org/3/library/dataclasses.html#module-contents

from dataclasses import dataclass, KW_ONLY
from typing import assert_type, Literal

# If true, the __match_args__ tuple will be created from the list of non keyword-only parameters to the generated __init__() method

@dataclass(match_args=True)
class DC1:
    x: int
    _: KW_ONLY
    y: str

assert_type(DC1.__match_args__, tuple[Literal['x']])

# The match_args default is True

@dataclass
class DC2:
    x: int

assert_type(DC2.__match_args__, tuple[Literal['x']])

# __match_args__ is created even if __init__() is not generated

@dataclass(match_args=True, init=False)
class DC3:
    x: int = 0

assert_type(DC3.__match_args__, tuple[Literal['x']])

# If false, or if __match_args__ is already defined in the class, then __match_args__ will not be generated.

@dataclass(match_args=False)
class DC4:
    x: int

DC4.__match_args__  # E

@dataclass(match_args=True)
class DC5:
    __match_args__ = ()
    x: int

assert_type(DC5.__match_args__, tuple[()])
