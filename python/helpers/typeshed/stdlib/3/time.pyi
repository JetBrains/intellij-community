# Stubs for time
# Ron Murawski <ron@horizonchess.com>

# based on: http://docs.python.org/3.3/library/time.html#module-time
# see: http://nullege.com/codes/search?cq=time

import sys
from typing import Tuple, Union
from types import SimpleNamespace

TimeTuple = Tuple[int, int, int, int, int, int, int, int, int]

# ----- variables and constants -----
accept2dyear = False
altzone = 0
daylight = 0
timezone = 0
tzname = ... # type: Tuple[str, str]

if sys.version_info >= (3, 3) and sys.platform != 'win32':
    CLOCK_HIGHRES = 0  # Solaris only
    CLOCK_MONOTONIC = 0  # Unix only
    CLOCK_MONOTONIC_RAW = 0  # Linux 2.6.28 or later
    CLOCK_PROCESS_CPUTIME_ID = 0  # Unix only
    CLOCK_REALTIME = 0  # Unix only
    CLOCK_THREAD_CPUTIME_ID = 0  # Unix only


# ----- classes/methods -----
class struct_time:
    # this is supposed to be a namedtuple object
    # namedtuple is not yet implemented (see file: mypy/stubs/collections.py)
    # see: http://docs.python.org/3.2/library/time.html#time.struct_time
    # see: http://nullege.com/codes/search/time.struct_time
    # TODO: namedtuple() object problem
    #namedtuple __init__(self, int, int, int, int, int, int, int, int, int):
    #    ...
    tm_year = 0
    tm_mon = 0
    tm_mday = 0
    tm_hour = 0
    tm_min = 0
    tm_sec = 0
    tm_wday = 0
    tm_yday = 0
    tm_isdst = 0
    if sys.version_info >= (3, 3):
        tm_gmtoff = 0
        tm_zone = 'GMT'

# ----- functions -----
def asctime(t: Union[TimeTuple, struct_time, None] = ...) -> str: ...  # return current time
def clock() -> float: ...
def ctime(secs: Union[float, None] = ...) -> str: ...  # return current time
def gmtime(secs: Union[float, None] = ...) -> struct_time: ...  # return current time
def localtime(secs: Union[float, None] = ...) -> struct_time: ...  # return current time
def mktime(t: Union[TimeTuple, struct_time]) -> float: ...
def sleep(secs: Union[int, float]) -> None: ...
def strftime(format: str,
             t: Union[TimeTuple, struct_time, None] = ...) -> str: ...  # return current time
def strptime(string: str,
             format: str = ...) -> struct_time: ...
def time() -> float: ...
if sys.platform != 'win32':
    def tzset() -> None: ...  # Unix only

if sys.version_info >= (3, 3):
    def get_clock_info(str) -> SimpleNamespace: ...
    def monotonic() -> float: ...
    def perf_counter() -> float: ...
    def process_time() -> float: ...
    if sys.platform != 'win32':
        def clock_getres(int) -> float: ...  # Unix only
        def clock_gettime(int) -> float: ...  # Unix only
        def clock_settime(int, struct_time) -> float: ...  # Unix only
