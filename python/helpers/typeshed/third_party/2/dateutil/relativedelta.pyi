from typing import Any, List, Optional, Union
from datetime import date, datetime, timedelta

__all__ = ...  # type: List[str]


class weekday(object):
    def __init__(self, weekday: int, n: Optional[int]=...) -> None: ...

    def __call__(self, n: int) -> 'weekday': ...

    def __eq__(self, other) -> bool: ...

    def __repr__(self) -> str: ...

    weekday = ...  # type: int
    n = ...  # type: int

MO = ...  # type: weekday
TU = ...  # type: weekday
WE = ...  # type: weekday
TH = ...  # type: weekday
FR = ...  # type: weekday
SA = ...  # type: weekday
SU = ...  # type: weekday


class relativedelta(object):
    def __init__(self,
                 dt1: Optional[date]=...,
                 dt2: Optional[date]=...,
                 years: Optional[int]=..., months: Optional[int]=...,
                 days: Optional[int]=..., leapdays: Optional[int]=...,
                 weeks: Optional[int]=...,
                 hours: Optional[int]=..., minutes: Optional[int]=...,
                 seconds: Optional[int]=..., microseconds: Optional[int]=...,
                 year: Optional[int]=..., month: Optional[int]=...,
                 day: Optional[int]=...,
                 weekday: Optional[Union[int, weekday]]=...,
                 yearday: Optional[int]=...,
                 nlyearday: Optional[int]=...,
                 hour: Optional[int]=..., minute: Optional[int]=...,
                 second: Optional[int]=...,
                 microsecond: Optional[int]=...) -> None: ...

    @property
    def weeks(self) -> int: ...

    @weeks.setter
    def weeks(self, value: int) -> None: ...

    def normalized(self) -> 'relativedelta': ...

    def __add__(
        self,
        other: Union['relativedelta', timedelta, date, datetime]) -> 'relativedelta': ...

    def __radd__(
        self,
        other: Any) -> 'relativedelta': ...

    def __rsub__(
        self,
        other: Any) -> 'relativedelta': ...

    def __sub__(self, other: 'relativedelta') -> 'relativedelta': ...

    def __neg__(self) -> 'relativedelta': ...

    def __bool__(self) -> bool: ...

    def __nonzero__(self) -> bool: ...

    def __mul__(self, other: float) -> 'relativedelta': ...

    def __rmul__(self, other: float) -> 'relativedelta': ...

    def __eq__(self, other) -> bool: ...

    def __ne__(self, other: object) -> bool: ...

    def __div__(self, other: float) -> 'relativedelta': ...

    def __truediv__(self, other: float) -> 'relativedelta': ...

    def __repr__(self) -> str: ...
