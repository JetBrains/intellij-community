# Stubs for time
# Ron Murawski <ron@horizonchess.com>

# based on: http://docs.python.org/3.3/library/time.html#module-time
# see: http://nullege.com/codes/search?cq=time

import sys
from typing import Any, NamedTuple, Tuple, Union
from types import SimpleNamespace

TimeTuple = Tuple[int, int, int, int, int, int, int, int, int]

# ----- variables and constants -----
accept2dyear = False
altzone = 0
daylight = 0
timezone = 0
tzname = ...  # type: Tuple[str, str]

if sys.version_info >= (3, 3) and sys.platform != 'win32':
    CLOCK_HIGHRES = 0  # Solaris only
    CLOCK_MONOTONIC = 0  # Unix only
    CLOCK_MONOTONIC_RAW = 0  # Linux 2.6.28 or later
    CLOCK_PROCESS_CPUTIME_ID = 0  # Unix only
    CLOCK_REALTIME = 0  # Unix only
    CLOCK_THREAD_CPUTIME_ID = 0  # Unix only


if sys.version_info >= (3, 3):
    class struct_time(
        NamedTuple(
            '_struct_time',
            [('tm_year', int), ('tm_mon', int), ('tm_mday', int),
             ('tm_hour', int), ('tm_min', int), ('tm_sec', int),
             ('tm_wday', int), ('tm_yday', int), ('tm_isdst', int),
             ('tm_zone', str), ('tm_gmtoff', int)]
        )
    ):
        def __init__(
            self,
            o: Union[
                Tuple[int, int, int, int, int, int, int, int, int],
                Tuple[int, int, int, int, int, int, int, int, int, str],
                Tuple[int, int, int, int, int, int, int, int, int, str, int]
            ],
            _arg: Any = ...,
        ) -> None: ...
else:
    class struct_time(
        NamedTuple(
            '_struct_time',
            [('tm_year', int), ('tm_mon', int), ('tm_mday', int),
             ('tm_hour', int), ('tm_min', int), ('tm_sec', int),
             ('tm_wday', int), ('tm_yday', int), ('tm_isdst', int)]
        )
    ):
        def __init__(self, o: TimeTuple, _arg: Any = ...) -> None: ...


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
    def get_clock_info(name: str) -> SimpleNamespace: ...
    def monotonic() -> float: ...
    def perf_counter() -> float: ...
    def process_time() -> float: ...
    if sys.platform != 'win32':
        def clock_getres(clk_id: int) -> float: ...  # Unix only
        def clock_gettime(clk_id: int) -> float: ...  # Unix only
        def clock_settime(clk_id: int, time: struct_time) -> float: ...  # Unix only
