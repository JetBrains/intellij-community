# Stubs for pytz (Python 3.5)

import datetime as dt
from typing import Optional, List, Set, Dict  # NOQA

all_timezones = ...  # type: List
all_timezones_set = ...  # type: Set
common_timezones = ...  # type: List
common_timezones_set = ...  # type: Set
country_timezones = ...  # type: Dict
country_names = ...  # type: Dict


class UTC(dt.tzinfo):
    zone = ...  # type: str
    def fromutc(self, dt: dt.datetime) -> dt.datetime: ...
    def utcoffset(self, dt: Optional[dt.datetime]) -> dt.timedelta: ...  # type: ignore
    def tzname(self, dt: Optional[dt.datetime]) -> str: ...
    def dst(self, dt: Optional[dt.datetime]) -> dt.timedelta: ...  # type: ignore
    def localize(self, dt: dt.datetime, is_dst: bool=...) -> dt.datetime: ...
    def normalize(self, dt: dt.datetime, is_dst: bool=...) -> dt.datetime: ...

utc = ...  # type: UTC

def timezone(zone: str) -> UTC: ...
