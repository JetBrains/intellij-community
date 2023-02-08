from _typeshed import Self
from datetime import date, datetime, timedelta
from typing import SupportsFloat, TypeVar, overload
from typing_extensions import TypeAlias

from ._common import weekday

# We need the extra "Self" TypeVar to avoid overlapping __add__/__radd__ complaints from mypy
_SelfT = TypeVar("_SelfT", bound=relativedelta)
_DateT = TypeVar("_DateT", date, datetime)
# Work around attribute and type having the same name.
_weekday: TypeAlias = weekday

MO: weekday
TU: weekday
WE: weekday
TH: weekday
FR: weekday
SA: weekday
SU: weekday

class relativedelta:
    years: int
    months: int
    days: int
    leapdays: int
    hours: int
    minutes: int
    seconds: int
    microseconds: int
    year: int | None
    month: int | None
    weekday: _weekday | None
    day: int | None
    hour: int | None
    minute: int | None
    second: int | None
    microsecond: int | None
    def __init__(
        self,
        dt1: date | None = ...,
        dt2: date | None = ...,
        years: int | None = ...,
        months: int | None = ...,
        days: int | None = ...,
        leapdays: int | None = ...,
        weeks: int | None = ...,
        hours: int | None = ...,
        minutes: int | None = ...,
        seconds: int | None = ...,
        microseconds: int | None = ...,
        year: int | None = ...,
        month: int | None = ...,
        day: int | None = ...,
        weekday: int | _weekday | None = ...,
        yearday: int | None = ...,
        nlyearday: int | None = ...,
        hour: int | None = ...,
        minute: int | None = ...,
        second: int | None = ...,
        microsecond: int | None = ...,
    ) -> None: ...
    @property
    def weeks(self) -> int: ...
    @weeks.setter
    def weeks(self, value: int) -> None: ...
    def normalized(self: Self) -> Self: ...
    # TODO: use Union when mypy will handle it properly in overloaded operator
    # methods (#2129, #1442, #1264 in mypy)
    @overload
    def __add__(self: _SelfT, other: relativedelta) -> _SelfT: ...  # noqa: Y019
    @overload
    def __add__(self: _SelfT, other: timedelta) -> _SelfT: ...  # noqa: Y019
    @overload
    def __add__(self, other: _DateT) -> _DateT: ...
    @overload
    def __radd__(self: _SelfT, other: relativedelta) -> _SelfT: ...  # noqa: Y019
    @overload
    def __radd__(self: _SelfT, other: timedelta) -> _SelfT: ...  # noqa: Y019
    @overload
    def __radd__(self, other: _DateT) -> _DateT: ...
    @overload
    def __rsub__(self: Self, other: relativedelta) -> Self: ...
    @overload
    def __rsub__(self: Self, other: timedelta) -> Self: ...
    @overload
    def __rsub__(self, other: _DateT) -> _DateT: ...
    def __sub__(self: Self, other: relativedelta) -> Self: ...
    def __neg__(self: Self) -> Self: ...
    def __bool__(self) -> bool: ...
    def __nonzero__(self) -> bool: ...
    def __mul__(self: Self, other: SupportsFloat) -> Self: ...
    def __rmul__(self: Self, other: SupportsFloat) -> Self: ...
    def __eq__(self, other: object) -> bool: ...
    def __ne__(self, other: object) -> bool: ...
    def __div__(self: Self, other: SupportsFloat) -> Self: ...
    def __truediv__(self: Self, other: SupportsFloat) -> Self: ...
    def __abs__(self: Self) -> Self: ...
    def __hash__(self) -> int: ...
