# Stubs for re
# Ron Murawski <ron@horizonchess.com>
# 'bytes' support added by Jukka Lehtosalo

# based on: http://docs.python.org/3.2/library/re.html
# and http://hg.python.org/cpython/file/618ea5612e83/Lib/re.py

from typing import (
    List, Iterator, overload, Callable, Tuple, Sequence, Dict,
    Generic, AnyStr, Match, Pattern, Any, Optional
)

# ----- re variables and constants -----
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

class error(Exception): ...

@overload
def compile(pattern: AnyStr, flags: int = ...) -> Pattern[AnyStr]: ...
@overload
def compile(pattern: Pattern[AnyStr], flags: int = ...) -> Pattern[AnyStr]: ...

@overload
def search(pattern: AnyStr, string: AnyStr, flags: int = ...) -> Match[AnyStr]: ...
@overload
def search(pattern: Pattern[AnyStr], string: AnyStr, flags: int = ...) -> Match[AnyStr]: ...

@overload
def match(pattern: AnyStr, string: AnyStr, flags: int = ...) -> Match[AnyStr]: ...
@overload
def match(pattern: Pattern[AnyStr], string: AnyStr, flags: int = ...) -> Match[AnyStr]: ...

# New in Python 3.4
@overload
def fullmatch(pattern: AnyStr, string: AnyStr, flags: int = ...) -> Optional[Match[AnyStr]]: ...
@overload
def fullmatch(pattern: Pattern[AnyStr], string: AnyStr, flags: int = ...) -> Optional[Match[AnyStr]]: ...

@overload
def split(pattern: AnyStr, string: AnyStr,
          maxsplit: int = ..., flags: int = ...) -> List[AnyStr]: ...
@overload
def split(pattern: Pattern[AnyStr], string: AnyStr,
          maxsplit: int = ..., flags: int = ...) -> List[AnyStr]: ...

@overload
def findall(pattern: AnyStr, string: AnyStr, flags: int = ...) -> List[Any]: ...
@overload
def findall(pattern: Pattern[AnyStr], string: AnyStr, flags: int = ...) -> List[Any]: ...

# Return an iterator yielding match objects over all non-overlapping matches
# for the RE pattern in string. The string is scanned left-to-right, and
# matches are returned in the order found. Empty matches are included in the
# result unless they touch the beginning of another match.
@overload
def finditer(pattern: AnyStr, string: AnyStr,
             flags: int = ...) -> Iterator[Match[AnyStr]]: ...
@overload
def finditer(pattern: Pattern[AnyStr], string: AnyStr,
             flags: int = ...) -> Iterator[Match[AnyStr]]: ...

@overload
def sub(pattern: AnyStr, repl: AnyStr, string: AnyStr, count: int = ...,
        flags: int = ...) -> AnyStr: ...
@overload
def sub(pattern: AnyStr, repl: Callable[[Match[AnyStr]], AnyStr],
        string: AnyStr, count: int = ..., flags: int = ...) -> AnyStr: ...
@overload
def sub(pattern: Pattern[AnyStr], repl: AnyStr, string: AnyStr, count: int = ...,
        flags: int = ...) -> AnyStr: ...
@overload
def sub(pattern: Pattern[AnyStr], repl: Callable[[Match[AnyStr]], AnyStr],
        string: AnyStr, count: int = ..., flags: int = ...) -> AnyStr: ...

@overload
def subn(pattern: AnyStr, repl: AnyStr, string: AnyStr, count: int = ...,
         flags: int = ...) -> Tuple[AnyStr, int]: ...
@overload
def subn(pattern: AnyStr, repl: Callable[[Match[AnyStr]], AnyStr],
         string: AnyStr, count: int = ...,
         flags: int = ...) -> Tuple[AnyStr, int]: ...
@overload
def subn(pattern: Pattern[AnyStr], repl: AnyStr, string: AnyStr, count: int = ...,
         flags: int = ...) -> Tuple[AnyStr, int]: ...
@overload
def subn(pattern: Pattern[AnyStr], repl: Callable[[Match[AnyStr]], AnyStr],
         string: AnyStr, count: int = ...,
         flags: int = ...) -> Tuple[AnyStr, int]: ...

def escape(string: AnyStr) -> AnyStr: ...

def purge() -> None: ...
