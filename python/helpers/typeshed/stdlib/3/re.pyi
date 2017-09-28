# Stubs for re
# Ron Murawski <ron@horizonchess.com>
# 'bytes' support added by Jukka Lehtosalo

# based on: http://docs.python.org/3.2/library/re.html
# and http://hg.python.org/cpython/file/618ea5612e83/Lib/re.py

import sys
from typing import (
    List, Iterator, overload, Callable, Tuple, Sequence, Dict,
    Generic, AnyStr, Match, Pattern, Any, Optional, Union
)

# ----- re variables and constants -----
if sys.version_info >= (3, 6):
    import enum
    class RegexFlag(enum.IntFlag):
        A = 0
        ASCII = 0
        DEBUG = 0
        I = 0
        IGNORECASE = 0
        L = 0
        LOCALE = 0
        M = 0
        MULTILINE = 0
        S = 0
        DOTALL = 0
        X = 0
        VERBOSE = 0
        U = 0
        UNICODE = 0
        T = 0
        TEMPLATE = 0

    A = RegexFlag.A
    ASCII = RegexFlag.ASCII
    DEBUG = RegexFlag.DEBUG
    I = RegexFlag.I
    IGNORECASE = RegexFlag.IGNORECASE
    L = RegexFlag.L
    LOCALE = RegexFlag.LOCALE
    M = RegexFlag.M
    MULTILINE = RegexFlag.MULTILINE
    S = RegexFlag.S
    DOTALL = RegexFlag.DOTALL
    X = RegexFlag.X
    VERBOSE = RegexFlag.VERBOSE
    U = RegexFlag.U
    UNICODE = RegexFlag.UNICODE
    T = RegexFlag.T
    TEMPLATE = RegexFlag.TEMPLATE
    _FlagsType = Union[int, RegexFlag]
else:
    A = 0
    ASCII = 0
    DEBUG = 0
    I = 0
    IGNORECASE = 0
    L = 0
    LOCALE = 0
    M = 0
    MULTILINE = 0
    S = 0
    DOTALL = 0
    X = 0
    VERBOSE = 0
    U = 0
    UNICODE = 0
    T = 0
    TEMPLATE = 0
    _FlagsType = int

class error(Exception): ...

@overload
def compile(pattern: AnyStr, flags: _FlagsType = ...) -> Pattern[AnyStr]: ...
@overload
def compile(pattern: Pattern[AnyStr], flags: _FlagsType = ...) -> Pattern[AnyStr]: ...

@overload
def search(pattern: AnyStr, string: AnyStr, flags: _FlagsType = ...) -> Match[AnyStr]: ...
@overload
def search(pattern: Pattern[AnyStr], string: AnyStr, flags: _FlagsType = ...) -> Match[AnyStr]: ...

@overload
def match(pattern: AnyStr, string: AnyStr, flags: _FlagsType = ...) -> Match[AnyStr]: ...
@overload
def match(pattern: Pattern[AnyStr], string: AnyStr, flags: _FlagsType = ...) -> Match[AnyStr]: ...

# New in Python 3.4
@overload
def fullmatch(pattern: AnyStr, string: AnyStr, flags: _FlagsType = ...) -> Optional[Match[AnyStr]]: ...
@overload
def fullmatch(pattern: Pattern[AnyStr], string: AnyStr, flags: _FlagsType = ...) -> Optional[Match[AnyStr]]: ...

@overload
def split(pattern: AnyStr, string: AnyStr,
          maxsplit: int = ..., flags: _FlagsType = ...) -> List[AnyStr]: ...
@overload
def split(pattern: Pattern[AnyStr], string: AnyStr,
          maxsplit: int = ..., flags: _FlagsType = ...) -> List[AnyStr]: ...

@overload
def findall(pattern: AnyStr, string: AnyStr, flags: _FlagsType = ...) -> List[Any]: ...
@overload
def findall(pattern: Pattern[AnyStr], string: AnyStr, flags: _FlagsType = ...) -> List[Any]: ...

# Return an iterator yielding match objects over all non-overlapping matches
# for the RE pattern in string. The string is scanned left-to-right, and
# matches are returned in the order found. Empty matches are included in the
# result unless they touch the beginning of another match.
@overload
def finditer(pattern: AnyStr, string: AnyStr,
             flags: _FlagsType = ...) -> Iterator[Match[AnyStr]]: ...
@overload
def finditer(pattern: Pattern[AnyStr], string: AnyStr,
             flags: _FlagsType = ...) -> Iterator[Match[AnyStr]]: ...

@overload
def sub(pattern: AnyStr, repl: AnyStr, string: AnyStr, count: int = ...,
        flags: _FlagsType = ...) -> AnyStr: ...
@overload
def sub(pattern: AnyStr, repl: Callable[[Match[AnyStr]], AnyStr],
        string: AnyStr, count: int = ..., flags: _FlagsType = ...) -> AnyStr: ...
@overload
def sub(pattern: Pattern[AnyStr], repl: AnyStr, string: AnyStr, count: int = ...,
        flags: _FlagsType = ...) -> AnyStr: ...
@overload
def sub(pattern: Pattern[AnyStr], repl: Callable[[Match[AnyStr]], AnyStr],
        string: AnyStr, count: int = ..., flags: _FlagsType = ...) -> AnyStr: ...

@overload
def subn(pattern: AnyStr, repl: AnyStr, string: AnyStr, count: int = ...,
         flags: _FlagsType = ...) -> Tuple[AnyStr, int]: ...
@overload
def subn(pattern: AnyStr, repl: Callable[[Match[AnyStr]], AnyStr],
         string: AnyStr, count: int = ...,
         flags: _FlagsType = ...) -> Tuple[AnyStr, int]: ...
@overload
def subn(pattern: Pattern[AnyStr], repl: AnyStr, string: AnyStr, count: int = ...,
         flags: _FlagsType = ...) -> Tuple[AnyStr, int]: ...
@overload
def subn(pattern: Pattern[AnyStr], repl: Callable[[Match[AnyStr]], AnyStr],
         string: AnyStr, count: int = ...,
         flags: _FlagsType = ...) -> Tuple[AnyStr, int]: ...

def escape(string: AnyStr) -> AnyStr: ...

def purge() -> None: ...
def template(pattern: Union[AnyStr, Pattern[AnyStr]], flags: _FlagsType = ...) -> Pattern[AnyStr]: ...
