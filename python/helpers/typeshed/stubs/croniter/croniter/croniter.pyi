import datetime
from _typeshed import Self
from collections.abc import Iterator
from typing import Any
from typing_extensions import Literal, TypeAlias

_RetType: TypeAlias = type[float | datetime.datetime]

class CroniterError(ValueError): ...
class CroniterBadTypeRangeError(TypeError): ...
class CroniterBadCronError(CroniterError): ...
class CroniterUnsupportedSyntaxError(CroniterBadCronError): ...
class CroniterBadDateError(CroniterError): ...
class CroniterNotAlphaError(CroniterError): ...

def datetime_to_timestamp(d: datetime.datetime) -> float: ...

class croniter(Iterator[Any]):
    MONTHS_IN_YEAR: Literal[12]
    RANGES: tuple[tuple[int, int], ...]
    DAYS: tuple[
        Literal[31],
        Literal[28],
        Literal[31],
        Literal[30],
        Literal[31],
        Literal[30],
        Literal[31],
        Literal[31],
        Literal[30],
        Literal[31],
        Literal[30],
        Literal[31],
    ]
    ALPHACONV: tuple[dict[str, Any], ...]
    LOWMAP: tuple[dict[int, Any], ...]
    LEN_MEANS_ALL: tuple[int, ...]
    bad_length: str
    tzinfo: datetime.tzinfo | None
    cur: float
    expanded: list[list[str]]
    start_time: float
    dst_start_time: float
    nth_weekday_of_month: dict[str, Any]
    def __init__(
        self,
        expr_format: str,
        start_time: float | datetime.datetime | None = ...,
        ret_type: _RetType | None = ...,
        day_or: bool = ...,
        max_years_between_matches: int | None = ...,
        is_prev: bool = ...,
        hash_id: str | bytes | None = ...,  # unicode not accepted on python 2
    ) -> None: ...
    # Most return value depend on ret_type, which can be passed in both as a method argument and as
    # a constructor argument.
    def get_next(self, ret_type: _RetType | None = ..., start_time: float | datetime.datetime | None = ...) -> Any: ...
    def get_prev(self, ret_type: _RetType | None = ...) -> Any: ...
    def get_current(self, ret_type: _RetType | None = ...) -> Any: ...
    def set_current(self, start_time: float | datetime.datetime | None, force: bool = ...) -> float: ...
    def __iter__(self: Self) -> Self: ...
    def next(
        self, ret_type: _RetType | None = ..., start_time: float | datetime.datetime | None = ..., is_prev: bool | None = ...
    ) -> Any: ...
    __next__ = next
    def all_next(self, ret_type: _RetType | None = ...) -> Iterator[Any]: ...
    def all_prev(self, ret_type: _RetType | None = ...) -> Iterator[Any]: ...
    def iter(self, ret_type: _RetType | None = ...) -> Iterator[Any]: ...
    def is_leap(self, year: int) -> bool: ...
    @classmethod
    def expand(cls, expr_format: str, hash_id: str | bytes | None = ...) -> tuple[list[list[str]], dict[str, Any]]: ...
    @classmethod
    def is_valid(cls, expression: str, hash_id: str | bytes | None = ...) -> bool: ...
    @classmethod
    def match(cls, cron_expression: str, testdate: float | datetime.datetime | None, day_or: bool = ...) -> bool: ...

def croniter_range(
    start: float | datetime.datetime,
    stop: float | datetime.datetime,
    expr_format: str,
    ret_type: _RetType | None = ...,
    day_or: bool = ...,
    exclude_ends: bool = ...,
    _croniter: type[croniter] | None = ...,
) -> Iterator[Any]: ...
