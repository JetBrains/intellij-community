from typing import Literal
from m import *


v1: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">A.X</warning>]

X = Color.R
v2: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">X</warning>]

v3: Literal[Color.G]
v4: Literal[Color.RED]
v5: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">Color.foo</warning>]
v6: Literal[Color.bar]

v7: Literal[SuperEnum.PINK]

v8: Literal[E.FOO]
v9: Literal[E.BAR]
v10: Literal[E.BUZ]
v11: Literal[E.QUX]
v12: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">E.meth2</warning>]
