import datetime
import sys
from _typeshed import Incomplete

if sys.platform == "win32":
    handle: Incomplete
    tzparent: Incomplete
    parentsize: Incomplete
    localkey: Incomplete
    WEEKS: Incomplete
    def list_timezones(): ...

    class win32tz(datetime.tzinfo):
        data: Incomplete
        def __init__(self, name) -> None: ...
        def utcoffset(self, dt): ...
        def dst(self, dt): ...
        def tzname(self, dt): ...

    def pickNthWeekday(year, month, dayofweek, hour, minute, whichweek): ...

    class win32tz_data:
        display: Incomplete
        dstname: Incomplete
        stdname: Incomplete
        stdoffset: Incomplete
        dstoffset: Incomplete
        stdmonth: Incomplete
        stddayofweek: Incomplete
        stdweeknumber: Incomplete
        stdhour: Incomplete
        stdminute: Incomplete
        dstmonth: Incomplete
        dstdayofweek: Incomplete
        dstweeknumber: Incomplete
        dsthour: Incomplete
        dstminute: Incomplete
        def __init__(self, path) -> None: ...

    def valuesToDict(key): ...
