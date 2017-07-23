# Stubs for locale

from typing import Any, Iterable, List, Mapping, Optional, Sequence, Tuple, Union
import sys

# workaround for mypy#2010
if sys.version_info < (3,):
    from __builtin__ import str as _str
else:
    from builtins import str as _str

CODESET = ...  # type: int
D_T_FMT = ...  # type: int
D_FMT = ...  # type: int
T_FMT = ...  # type: int
T_FMT_AMPM = ...  # type: int

DAY_1 = ...  # type: int
DAY_2 = ...  # type: int
DAY_3 = ...  # type: int
DAY_4 = ...  # type: int
DAY_5 = ...  # type: int
DAY_6 = ...  # type: int
DAY_7 = ...  # type: int
ABDAY_1 = ...  # type: int
ABDAY_2 = ...  # type: int
ABDAY_3 = ...  # type: int
ABDAY_4 = ...  # type: int
ABDAY_5 = ...  # type: int
ABDAY_6 = ...  # type: int
ABDAY_7 = ...  # type: int

MON_1 = ...  # type: int
MON_2 = ...  # type: int
MON_3 = ...  # type: int
MON_4 = ...  # type: int
MON_5 = ...  # type: int
MON_6 = ...  # type: int
MON_7 = ...  # type: int
MON_8 = ...  # type: int
MON_9 = ...  # type: int
MON_10 = ...  # type: int
MON_11 = ...  # type: int
MON_12 = ...  # type: int
ABMON_1 = ...  # type: int
ABMON_2 = ...  # type: int
ABMON_3 = ...  # type: int
ABMON_4 = ...  # type: int
ABMON_5 = ...  # type: int
ABMON_6 = ...  # type: int
ABMON_7 = ...  # type: int
ABMON_8 = ...  # type: int
ABMON_9 = ...  # type: int
ABMON_10 = ...  # type: int
ABMON_11 = ...  # type: int
ABMON_12 = ...  # type: int

RADIXCHAR = ...  # type: int
THOUSEP = ...  # type: int
YESEXPR = ...  # type: int
NOEXPR = ...  # type: int
CRNCYSTR = ...  # type: int

ERA = ...  # type: int
ERA_D_T_FMT = ...  # type: int
ERA_D_FMT = ...  # type: int
ERA_T_FMT = ...  # type: int

ALT_DIGITS = ...  # type: int

LC_CTYPE = ...  # type: int
LC_COLLATE = ...  # type: int
LC_TIME = ...  # type: int
LC_MONETARY = ...  # type: int
LC_MESSAGES = ...  # type: int
LC_NUMERIC = ...  # type: int
LC_ALL = ...  # type: int

CHAR_MAX = ...  # type: int

class Error(Exception): ...

def setlocale(category: int,
              locale: Union[_str, Iterable[_str], None] = ...) -> _str: ...
def localeconv() -> Mapping[_str, Union[int, _str, List[int]]]: ...
def nl_langinfo(option: int) -> _str: ...
def getdefaultlocale(envvars: Tuple[_str, ...] = ...) -> Tuple[Optional[_str], Optional[_str]]: ...
def getlocale(category: int = ...) -> Sequence[_str]: ...
def getpreferredencoding(do_setlocale: bool = ...) -> _str: ...
def normalize(localename: _str) -> _str: ...
def resetlocale(category: int = ...) -> None: ...
def strcoll(string1: _str, string2: _str) -> int: ...
def strxfrm(string: _str) -> _str: ...
def format(format: _str, val: int, grouping: bool = ...,
           monetary: bool = ...) -> _str: ...
def format_string(format: _str, val: Sequence[Any],
                  grouping: bool = ...) -> _str: ...
def currency(val: int, symbol: bool = ..., grouping: bool = ...,
             international: bool = ...) -> _str: ...
if sys.version_info >= (3, 5):
    def delocalize(string: _str) -> None: ...
def atof(string: _str) -> float: ...
def atoi(string: _str) -> int: ...
def str(float: float) -> _str: ...
