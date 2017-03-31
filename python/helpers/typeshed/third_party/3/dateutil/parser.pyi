from typing import List, Tuple, Optional, Callable, Union, IO, Any, Dict
from datetime import datetime

__all__ = ...  # type: List[str]


class parserinfo:
    JUMP = ...  # type: List[str]
    WEEKDAYS = ...  # type: List[Tuple[str, str]]
    MONTHS = ...  # type: List[Tuple[str, str]]
    HMS = ...  # type: List[Tuple[str, str, str]]
    AMPM = ...  # type: List[Tuple[str, str, str]]
    UTCZONE = ...  # type: List[str]
    PERTAIN = ...  # type: List[str]
    TZOFFSET = ...  # type: Dict[str, int]

    def __init__(self, dayfirst: bool=..., yearfirst: bool=...) -> None: ...
    def jump(self, name: str) -> bool: ...
    def weekday(self, name: str) -> str: ...
    def month(self, name: str) -> str: ...
    def hms(self, name: str) -> str: ...
    def ampm(self, name: str) -> str: ...
    def pertain(self, name: str) -> bool: ...
    def utczone(self, name: str) -> bool: ...
    def tzoffset(self, name: str) -> int: ...
    def convertyear(self, year: int) -> int: ...
    def validate(self, year: datetime) -> bool: ...


class parser:
    def __init__(self, info: parserinfo=...) -> None: ...

    def parse(
        self,
        timestr: Union[str, bytes, IO[Any]],
        default: Optional[datetime],
        ignoretz: bool=...,
        tzinfos: Any =...,
    ) -> datetime: ...

DEFAULTPARSER = ...  # type: parser


def parse(timestr: Union[str, bytes, IO[Any]], parserinfo: parserinfo=..., **kwargs) -> datetime:
    ...


class _tzparser:
    ...


DEFAULTTZPARSER = ...  # type: _tzparser
