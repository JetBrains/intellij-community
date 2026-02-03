"""
Tests the semantics of the Literal special form.
"""

from typing import Literal
from typing import Literal as L


v1: Literal[3] = 3
v2: Literal[3] = 4  # E

v3: L[-3] = -3


# > Literal[20] and Literal[0x14] are equivalent
def func1(a: Literal[20], b: Literal[0x14], c: Literal[0b10100]):
    x1: Literal[0x14] = a
    x2: Literal[0x14] = b
    x3: Literal[0x14] = c


# > Literal[0] and Literal[False] are not equivalent
def func2(a: Literal[0], b: Literal[False]):
    x1: Literal[False] = a  # E
    x2: Literal[0] = b  # E


# > Given some value v that is a member of type T, the type Literal[v] shall
# > be treated as a subtype of T. For example, Literal[3] is a subtype of int.
def func3(a: L[3, 4, 5]):
    b = a.__add__(3)
    c = a + 3
    a += 3 # E


# > When a Literal is parameterized with more than one value, it’s treated
# > as exactly equivalent to the union of those types.
def func4(a: L[None, 3] | L[3, "foo", b"bar", True]):
    x1: Literal[3, b"bar", True, "foo", None] = a
    a = x1
