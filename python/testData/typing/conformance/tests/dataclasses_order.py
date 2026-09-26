"""
Tests the synthesized comparison methods for dataclasses.
"""

from dataclasses import dataclass

@dataclass(order=True)
class DC1:
    a: str
    b: int


@dataclass(order=True)
class DC2:
    a: str
    b: int


dc1_1 = DC1("", 0)
dc1_2 = DC1("", 0)

if dc1_1 < dc1_2:
    pass

if dc1_1 <= dc1_2:
    pass

if dc1_1 > dc1_2:
    pass

if dc1_1 >= dc1_2:
    pass

if dc1_1 == dc1_2:
    pass

if dc1_1 != dc1_2:
    pass

if dc1_1 == None:
    pass

if dc1_1 != None:
    pass

dc2_1 = DC2("hi", 2)

# This should generate an error because the types are
# incompatible.
if dc1_1 < dc2_1:  # E:
    pass

if dc1_1 != dc2_1:
    pass
