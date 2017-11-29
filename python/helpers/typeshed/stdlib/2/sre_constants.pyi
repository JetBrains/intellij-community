# Source: https://hg.python.org/cpython/file/2.7/Lib/sre_constants.py

from typing import Dict, List, TypeVar

MAGIC = ...  # type: int
MAXREPEAT = ...  # type: int

class error(Exception): ...

FAILURE = ...  # type: str
SUCCESS = ...  # type: str
ANY = ...  # type: str
ANY_ALL = ...  # type: str
ASSERT = ...  # type: str
ASSERT_NOT = ...  # type: str
AT = ...  # type: str
BIGCHARSET = ...  # type: str
BRANCH = ...  # type: str
CALL = ...  # type: str
CATEGORY = ...  # type: str
CHARSET = ...  # type: str
GROUPREF = ...  # type: str
GROUPREF_IGNORE = ...  # type: str
GROUPREF_EXISTS = ...  # type: str
IN = ...  # type: str
IN_IGNORE = ...  # type: str
INFO = ...  # type: str
JUMP = ...  # type: str
LITERAL = ...  # type: str
LITERAL_IGNORE = ...  # type: str
MARK = ...  # type: str
MAX_REPEAT = ...  # type: str
MAX_UNTIL = ...  # type: str
MIN_REPEAT = ...  # type: str
MIN_UNTIL = ...  # type: str
NEGATE = ...  # type: str
NOT_LITERAL = ...  # type: str
NOT_LITERAL_IGNORE = ...  # type: str
RANGE = ...  # type: str
REPEAT = ...  # type: str
REPEAT_ONE = ...  # type: str
SUBPATTERN = ...  # type: str
MIN_REPEAT_ONE = ...  # type: str
AT_BEGINNING = ...  # type: str
AT_BEGINNING_LINE = ...  # type: str
AT_BEGINNING_STRING = ...  # type: str
AT_BOUNDARY = ...  # type: str
AT_NON_BOUNDARY = ...  # type: str
AT_END = ...  # type: str
AT_END_LINE = ...  # type: str
AT_END_STRING = ...  # type: str
AT_LOC_BOUNDARY = ...  # type: str
AT_LOC_NON_BOUNDARY = ...  # type: str
AT_UNI_BOUNDARY = ...  # type: str
AT_UNI_NON_BOUNDARY = ...  # type: str
CATEGORY_DIGIT = ...  # type: str
CATEGORY_NOT_DIGIT = ...  # type: str
CATEGORY_SPACE = ...  # type: str
CATEGORY_NOT_SPACE = ...  # type: str
CATEGORY_WORD = ...  # type: str
CATEGORY_NOT_WORD = ...  # type: str
CATEGORY_LINEBREAK = ...  # type: str
CATEGORY_NOT_LINEBREAK = ...  # type: str
CATEGORY_LOC_WORD = ...  # type: str
CATEGORY_LOC_NOT_WORD = ...  # type: str
CATEGORY_UNI_DIGIT = ...  # type: str
CATEGORY_UNI_NOT_DIGIT = ...  # type: str
CATEGORY_UNI_SPACE = ...  # type: str
CATEGORY_UNI_NOT_SPACE = ...  # type: str
CATEGORY_UNI_WORD = ...  # type: str
CATEGORY_UNI_NOT_WORD = ...  # type: str
CATEGORY_UNI_LINEBREAK = ...  # type: str
CATEGORY_UNI_NOT_LINEBREAK = ...  # type: str

_T = TypeVar('_T')
def makedict(list: List[_T]) -> Dict[_T, int]: ...

OP_IGNORE = ...  # type: Dict[str, str]
AT_MULTILINE = ...  # type: Dict[str, str]
AT_LOCALE = ...  # type: Dict[str, str]
AT_UNICODE = ...  # type: Dict[str, str]
CH_LOCALE = ...  # type: Dict[str, str]
CH_UNICODE = ...  # type: Dict[str, str]
SRE_FLAG_TEMPLATE = ...  # type: int
SRE_FLAG_IGNORECASE = ...  # type: int
SRE_FLAG_LOCALE = ...  # type: int
SRE_FLAG_MULTILINE = ...  # type: int
SRE_FLAG_DOTALL = ...  # type: int
SRE_FLAG_UNICODE = ...  # type: int
SRE_FLAG_VERBOSE = ...  # type: int
SRE_FLAG_DEBUG = ...  # type: int
SRE_INFO_PREFIX = ...  # type: int
SRE_INFO_LITERAL = ...  # type: int
SRE_INFO_CHARSET = ...  # type: int
