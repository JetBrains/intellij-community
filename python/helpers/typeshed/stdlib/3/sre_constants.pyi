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


# Stubgen above; manually defined constants below (dynamic at runtime)

# from OPCODES
FAILURE = ...  # type: _NamedIntConstant
SUCCESS = ...  # type: _NamedIntConstant
ANY = ...  # type: _NamedIntConstant
ANY_ALL = ...  # type: _NamedIntConstant
ASSERT = ...  # type: _NamedIntConstant
ASSERT_NOT = ...  # type: _NamedIntConstant
AT = ...  # type: _NamedIntConstant
BRANCH = ...  # type: _NamedIntConstant
CALL = ...  # type: _NamedIntConstant
CATEGORY = ...  # type: _NamedIntConstant
CHARSET = ...  # type: _NamedIntConstant
BIGCHARSET = ...  # type: _NamedIntConstant
GROUPREF = ...  # type: _NamedIntConstant
GROUPREF_EXISTS = ...  # type: _NamedIntConstant
GROUPREF_IGNORE = ...  # type: _NamedIntConstant
IN = ...  # type: _NamedIntConstant
IN_IGNORE = ...  # type: _NamedIntConstant
INFO = ...  # type: _NamedIntConstant
JUMP = ...  # type: _NamedIntConstant
LITERAL = ...  # type: _NamedIntConstant
LITERAL_IGNORE = ...  # type: _NamedIntConstant
MARK = ...  # type: _NamedIntConstant
MAX_UNTIL = ...  # type: _NamedIntConstant
MIN_UNTIL = ...  # type: _NamedIntConstant
NOT_LITERAL = ...  # type: _NamedIntConstant
NOT_LITERAL_IGNORE = ...  # type: _NamedIntConstant
NEGATE = ...  # type: _NamedIntConstant
RANGE = ...  # type: _NamedIntConstant
REPEAT = ...  # type: _NamedIntConstant
REPEAT_ONE = ...  # type: _NamedIntConstant
SUBPATTERN = ...  # type: _NamedIntConstant
MIN_REPEAT_ONE = ...  # type: _NamedIntConstant
RANGE_IGNORE = ...  # type: _NamedIntConstant
MIN_REPEAT = ...  # type: _NamedIntConstant
MAX_REPEAT = ...  # type: _NamedIntConstant

# from ATCODES
AT_BEGINNING = ...  # type: _NamedIntConstant
AT_BEGINNING_LINE = ...  # type: _NamedIntConstant
AT_BEGINNING_STRING = ...  # type: _NamedIntConstant
AT_BOUNDARY = ...  # type: _NamedIntConstant
AT_NON_BOUNDARY = ...  # type: _NamedIntConstant
AT_END = ...  # type: _NamedIntConstant
AT_END_LINE = ...  # type: _NamedIntConstant
AT_END_STRING = ...  # type: _NamedIntConstant
AT_LOC_BOUNDARY = ...  # type: _NamedIntConstant
AT_LOC_NON_BOUNDARY = ...  # type: _NamedIntConstant
AT_UNI_BOUNDARY = ...  # type: _NamedIntConstant
AT_UNI_NON_BOUNDARY = ...  # type: _NamedIntConstant

# from CHCODES
CATEGORY_DIGIT = ...  # type: _NamedIntConstant
CATEGORY_NOT_DIGIT = ...  # type: _NamedIntConstant
CATEGORY_SPACE = ...  # type: _NamedIntConstant
CATEGORY_NOT_SPACE = ...  # type: _NamedIntConstant
CATEGORY_WORD = ...  # type: _NamedIntConstant
CATEGORY_NOT_WORD = ...  # type: _NamedIntConstant
CATEGORY_LINEBREAK = ...  # type: _NamedIntConstant
CATEGORY_NOT_LINEBREAK = ...  # type: _NamedIntConstant
CATEGORY_LOC_WORD = ...  # type: _NamedIntConstant
CATEGORY_LOC_NOT_WORD = ...  # type: _NamedIntConstant
CATEGORY_UNI_DIGIT = ...  # type: _NamedIntConstant
CATEGORY_UNI_NOT_DIGIT = ...  # type: _NamedIntConstant
CATEGORY_UNI_SPACE = ...  # type: _NamedIntConstant
CATEGORY_UNI_NOT_SPACE = ...  # type: _NamedIntConstant
CATEGORY_UNI_WORD = ...  # type: _NamedIntConstant
CATEGORY_UNI_NOT_WORD = ...  # type: _NamedIntConstant
CATEGORY_UNI_LINEBREAK = ...  # type: _NamedIntConstant
CATEGORY_UNI_NOT_LINEBREAK = ...  # type: _NamedIntConstant
