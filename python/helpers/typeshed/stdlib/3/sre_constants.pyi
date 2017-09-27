# Source: https://github.com/python/cpython/blob/master/Lib/sre_constants.py

from typing import Any, Dict, List, Optional, Union

MAGIC = ...  # type: int

class error(Exception):
    msg = ...  # type: str
    pattern = ...  # type: Optional[Union[str, bytes]]
    pos = ...  # type: Optional[int]
    lineno = ...  # type: int
    colno = ...  # type: int
    def __init__(self, msg: str, pattern: Union[str, bytes] = ..., pos: int = ...) -> None: ...

class _NamedIntConstant(int):
    name = ...  # type: Any
    def __new__(cls, value: int, name: str): ...

MAXREPEAT = ...  # type: _NamedIntConstant
OPCODES = ...  # type: List[_NamedIntConstant]
ATCODES = ...  # type: List[_NamedIntConstant]
CHCODES = ...  # type: List[_NamedIntConstant]
OP_IGNORE = ...  # type: Dict[_NamedIntConstant, _NamedIntConstant]
AT_MULTILINE = ...  # type: Dict[_NamedIntConstant, _NamedIntConstant]
AT_LOCALE = ...  # type: Dict[_NamedIntConstant, _NamedIntConstant]
AT_UNICODE = ...  # type: Dict[_NamedIntConstant, _NamedIntConstant]
CH_LOCALE = ...  # type: Dict[_NamedIntConstant, _NamedIntConstant]
CH_UNICODE = ...  # type: Dict[_NamedIntConstant, _NamedIntConstant]
SRE_FLAG_TEMPLATE = ...  # type: int
SRE_FLAG_IGNORECASE = ...  # type: int
SRE_FLAG_LOCALE = ...  # type: int
SRE_FLAG_MULTILINE = ...  # type: int
SRE_FLAG_DOTALL = ...  # type: int
SRE_FLAG_UNICODE = ...  # type: int
SRE_FLAG_VERBOSE = ...  # type: int
SRE_FLAG_DEBUG = ...  # type: int
SRE_FLAG_ASCII = ...  # type: int
SRE_INFO_PREFIX = ...  # type: int
SRE_INFO_LITERAL = ...  # type: int
SRE_INFO_CHARSET = ...  # type: int
