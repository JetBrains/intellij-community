# Stubs for re
# Ron Murawski <ron@horizonchess.com>
# 'bytes' support added by Jukka Lehtosalo

# based on: http: //docs.python.org/2.7/library/re.html

from typing import (
    List, Iterator, overload, Callable, Tuple, Sequence, Dict,
    Generic, AnyStr, Match, Pattern, Any, Union
)

# ----- re variables and constants -----
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

class error(Exception): ...

@overload
def compile(pattern: AnyStr, flags: int = ...) -> Pattern[AnyStr]: ...
@overload
def compile(pattern: Pattern[AnyStr], flags: int = ...) -> Pattern[AnyStr]: ...

@overload
def search(pattern: Union[str, unicode], string: AnyStr, flags: int = ...) -> Match[AnyStr]: ...
@overload
def search(pattern: Union[Pattern[str], Pattern[unicode]], string: AnyStr, flags: int = ...) -> Match[AnyStr]: ...

@overload
def match(pattern: Union[str, unicode], string: AnyStr, flags: int = ...) -> Match[AnyStr]: ...
@overload
def match(pattern: Union[Pattern[str], Pattern[unicode]], string: AnyStr, flags: int = ...) -> Match[AnyStr]: ...

@overload
def split(pattern: Union[str, unicode], string: AnyStr,
          maxsplit: int = ..., flags: int = ...) -> List[AnyStr]: ...
@overload
def split(pattern: Union[Pattern[str], Pattern[unicode]], string: AnyStr,
          maxsplit: int = ..., flags: int = ...) -> List[AnyStr]: ...

@overload
def findall(pattern: Union[str, unicode], string: AnyStr, flags: int = ...) -> List[Any]: ...
@overload
def findall(pattern: Union[Pattern[str], Pattern[unicode]], string: AnyStr, flags: int = ...) -> List[Any]: ...

# Return an iterator yielding match objects over all non-overlapping matches
# for the RE pattern in string. The string is scanned left-to-right, and
# matches are returned in the order found. Empty matches are included in the
# result unless they touch the beginning of another match.
@overload
def finditer(pattern: Union[str, unicode], string: AnyStr,
             flags: int = ...) -> Iterator[Match[AnyStr]]: ...
@overload
def finditer(pattern: Union[Pattern[str], Pattern[unicode]], string: AnyStr,
             flags: int = ...) -> Iterator[Match[AnyStr]]: ...

@overload
def sub(pattern: Union[str, unicode], repl: AnyStr, string: AnyStr, count: int = ...,
        flags: int = ...) -> AnyStr: ...
@overload
def sub(pattern: Union[str, unicode], repl: Callable[[Match[AnyStr]], AnyStr],
        string: AnyStr, count: int = ..., flags: int = ...) -> AnyStr: ...
@overload
def sub(pattern: Union[Pattern[str], Pattern[unicode]], repl: AnyStr, string: AnyStr, count: int = ...,
        flags: int = ...) -> AnyStr: ...
@overload
def sub(pattern: Union[Pattern[str], Pattern[unicode]], repl: Callable[[Match[AnyStr]], AnyStr],
        string: AnyStr, count: int = ..., flags: int = ...) -> AnyStr: ...

@overload
def subn(pattern: Union[str, unicode], repl: AnyStr, string: AnyStr, count: int = ...,
         flags: int = ...) -> Tuple[AnyStr, int]: ...
@overload
def subn(pattern: Union[str, unicode], repl: Callable[[Match[AnyStr]], AnyStr],
         string: AnyStr, count: int = ...,
         flags: int = ...) -> Tuple[AnyStr, int]: ...
@overload
def subn(pattern: Union[Pattern[str], Pattern[unicode]], repl: AnyStr, string: AnyStr, count: int = ...,
         flags: int = ...) -> Tuple[AnyStr, int]: ...
@overload
def subn(pattern: Union[Pattern[str], Pattern[unicode]], repl: Callable[[Match[AnyStr]], AnyStr],
         string: AnyStr, count: int = ...,
         flags: int = ...) -> Tuple[AnyStr, int]: ...

def escape(string: AnyStr) -> AnyStr: ...

def purge() -> None: ...
