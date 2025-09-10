from _typeshed import Incomplete
from abc import abstractmethod
from typing import Any

from dateparser.conf import Settings
from dateparser.parser import _parser

class CalendarBase:
    parser: Any
    source: Any
    def __init__(self, source) -> None: ...
    def get_date(self): ...

class non_gregorian_parser(_parser):
    calendar_converter: Any
    default_year: Any
    default_month: Any
    default_day: Any
    non_gregorian_date_cls: Any
    @classmethod
    def to_latin(cls, source): ...
    @abstractmethod
    def handle_two_digit_year(self, year: int) -> int: ...
    @classmethod
    def parse(cls, datestring: str, settings: Settings) -> tuple[Incomplete, Incomplete]: ...  # type: ignore[override]
