#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
from collections import namedtuple

# A truncated version of the named tuple from the CPython `dis` module that contains
# only the fields necessary for the extraction of stepping variants.
Instruction = namedtuple(
    "_Instruction",
    [
        'opname',
        'opcode',
        'arg',
        'argval',
        'offset',
        'lineno',
    ]
)
