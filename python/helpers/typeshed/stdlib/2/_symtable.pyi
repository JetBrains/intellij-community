from typing import List, Dict

CELL = ...  # type: int
DEF_BOUND = ...  # type: int
DEF_FREE = ...  # type: int
DEF_FREE_CLASS = ...  # type: int
DEF_GLOBAL = ...  # type: int
DEF_IMPORT = ...  # type: int
DEF_LOCAL = ...  # type: int
DEF_PARAM = ...  # type: int
FREE = ...  # type: int
GLOBAL_EXPLICIT = ...  # type: int
GLOBAL_IMPLICIT = ...  # type: int
LOCAL = ...  # type: int
OPT_BARE_EXEC = ...  # type: int
OPT_EXEC = ...  # type: int
OPT_IMPORT_STAR = ...  # type: int
SCOPE_MASK = ...  # type: int
SCOPE_OFF = ...  # type: int
TYPE_CLASS = ...  # type: int
TYPE_FUNCTION = ...  # type: int
TYPE_MODULE = ...  # type: int
USE = ...  # type: int

class _symtable_entry(object):
    ...

class symtable(object):
    children = ...  # type: List[_symtable_entry]
    id = ...  # type: int
    lineno = ...  # type: int
    name = ...  # type: str
    nested = ...  # type: int
    optimized = ...  # type: int
    symbols = ...  # type: Dict[str, int]
    type = ...  # type: int
    varnames = ...  # type: List[str]

    def __init__(self, src: str, filename: str, startstr: str) -> None: ...
