# Stubs for pytz (Python 3.5)

import datetime
from typing import Optional, List, Set, Dict, Union

all_timezones = ...  # type: List
all_timezones_set = ...  # type: Set
common_timezones = ...  # type: List
common_timezones_set = ...  # type: Set
country_timezones = ...  # type: Dict
country_names = ...  # type: Dict


class _UTCclass(datetime.tzinfo):
    zone = ...  # type: str
    def fromutc(self, dt: datetime.datetime) -> datetime.datetime: ...
    def utcoffset(self, dt: Optional[datetime.datetime]) -> datetime.timedelta: ...  # type: ignore
    def tzname(self, dt: Optional[datetime.datetime]) -> str: ...
    def dst(self, dt: Optional[datetime.datetime]) -> datetime.timedelta: ...  # type: ignore
    def localize(self, dt: datetime.datetime, is_dst: bool = ...) -> datetime.datetime: ...
    def normalize(self, dt: datetime.datetime, is_dst: bool = ...) -> datetime.datetime: ...

utc = ...  # type: _UTCclass
UTC = ...  # type: _UTCclass


class _BaseTzInfo(datetime.tzinfo):
    zone = ...  # type: str

    def fromutc(self, dt: datetime.datetime) -> datetime.datetime: ...
    def localize(self, dt: datetime.datetime, is_dst: Optional[bool] = ...) -> datetime.datetime: ...
    def normalize(self, dt: datetime.datetime) -> datetime.datetime: ...


class _StaticTzInfo(_BaseTzInfo):
    def normalize(self, dt: datetime.datetime, is_dst: Optional[bool] = ...) -> datetime.datetime: ...


def timezone(zone: str) -> _BaseTzInfo: ...
