from typing import Any, SupportsIndex

from dateparser.calendars import non_gregorian_parser

class hijri:
    @classmethod
    def to_gregorian(cls, year: int | None = None, month: int | None = None, day: int | None = None) -> tuple[int, int, int]: ...
    @classmethod
    def from_gregorian(
        cls, year: SupportsIndex | None = None, month: SupportsIndex | None = None, day: SupportsIndex | None = None
    ) -> tuple[int, int, int]: ...
    @classmethod
    def month_length(cls, year: int, month: int) -> int: ...

class HijriDate:
    year: Any
    month: Any
    day: Any
    def __init__(self, year, month, day) -> None: ...
    def weekday(self): ...

class hijri_parser(non_gregorian_parser):
    calendar_converter: type[hijri]
    default_year: int
    default_month: int
    default_day: int
    non_gregorian_date_cls: type[HijriDate]
    def handle_two_digit_year(self, year: int) -> int: ...
