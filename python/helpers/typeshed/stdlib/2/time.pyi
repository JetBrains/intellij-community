"""Stub file for the 'time' module."""
# See https://docs.python.org/2/library/time.html

from typing import NamedTuple, Tuple, Union, Any

# ----- variables and constants -----
accept2dyear = False
altzone = 0
daylight = 0
timezone = 0
tzname = ... # type: Tuple[str, str]

class struct_time(NamedTuple('_struct_time',
                             [('tm_year', int), ('tm_mon', int), ('tm_mday', int),
                              ('tm_hour', int), ('tm_min', int), ('tm_sec', int),
                              ('tm_wday', int), ('tm_yday', int), ('tm_isdst', int)])):
    def __init__(self, o: Tuple[int, int, int,
                                int, int, int,
                                int, int, int], _arg: Any = ...) -> None: ...

_TIME_TUPLE = Tuple[int, int, int, int, int, int, int, int, int]

def asctime(t: struct_time = ...) -> str:
    raise ValueError()

def clock() -> float: ...

def ctime(secs: float = ...) -> str:
    raise ValueError()

def gmtime(secs: float = ...) -> struct_time: ...

def localtime(secs: float = ...) -> struct_time: ...

def mktime(t: struct_time) -> float:
    raise OverflowError()
    raise ValueError()

def sleep(secs: float) -> None: ...

def strftime(format: str, t: struct_time = ...) -> str:
    raise MemoryError()
    raise ValueError()

def strptime(string: str, format: str = ...) -> struct_time:
    raise ValueError()

def time() -> float:
    raise IOError()

def tzset() -> None: ...
