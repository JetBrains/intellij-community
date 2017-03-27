from typing import List, Dict, Sequence

cmp_op = ...  # type: Sequence[str]
hasconst = ...  # type: List[int]
hasname = ...  # type: List[int]
hasjrel = ...  # type: List[int]
hasjabs = ...  # type: List[int]
haslocal = ...  # type: List[int]
hascompare = ...  # type: List[int]
hasfree = ...  # type: List[int]
opname = ...  # type: List[str]

opmap = ...  # Dict[str, int]
HAVE_ARGUMENT = ...  # type: int
EXTENDED_ARG = ...  # type: int
hasnargs = ...  # type: List[int]

def stack_effect(opcode: int, oparg: int = ...) -> int: ...
