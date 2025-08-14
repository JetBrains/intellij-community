import datetime
from collections.abc import Generator, Iterator, Mapping, Sequence
from contextlib import contextmanager
from decimal import Decimal
from logging import Logger
from types import TracebackType
from typing import Any, Literal, Protocol, overload, type_check_only
from uuid import UUID

from typing_extensions import Self, TypeAlias

logger: Logger

# Protocol matching psycopg2.sql.Composable, to avoid depending psycopg2
@type_check_only
class _Composable(Protocol):
    def as_string(self, context: Any) -> str: ...
    def __add__(self, other: Self) -> _Composable: ...
    def __mul__(self, n: int) -> _Composable: ...

_ExecuteQuery: TypeAlias = str | _Composable

# Python types that can be adapted to SQL.
_SQLType: TypeAlias = (
    None
    | bool
    | int
    | float
    | Decimal
    | str
    | bytes
    | datetime.date
    | datetime.datetime
    | UUID
    | tuple[Any, ...]
    | list[Any]
)
_ExecuteParameters: TypeAlias = Sequence[_SQLType] | Mapping[str, _SQLType] | None

class CursorWrapper:
    cursor: Any
    db: Any
    def __init__(self, cursor: Any, db: Any) -> None: ...
    WRAP_ERROR_ATTRS: Any
    APPS_NOT_READY_WARNING_MSG: str

    def __getattr__(self, attr: str) -> Any: ...
    def __iter__(self) -> Iterator[tuple[Any, ...]]: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        exc_tb: TracebackType | None,
    ) -> None: ...
    def callproc(
        self, procname: str, params: Sequence[Any] | None = None, kparams: dict[str, int] | None = None
    ) -> Any: ...
    def execute(self, sql: _ExecuteQuery, params: _ExecuteParameters | None = None) -> Any: ...
    def executemany(self, sql: _ExecuteQuery, param_list: Sequence[_ExecuteParameters]) -> Any: ...

class CursorDebugWrapper(CursorWrapper):
    cursor: Any
    db: Any
    @contextmanager
    def debug_sql(
        self,
        sql: str | None = None,
        params: _ExecuteParameters | Sequence[_ExecuteParameters] | None = None,
        use_last_executed_query: bool = False,
        many: bool = False,
    ) -> Generator[None, None, None]: ...

@overload
def typecast_date(s: None | Literal[""]) -> None: ...  # type: ignore[overload-overlap]
@overload
def typecast_date(s: str) -> datetime.date: ...
@overload
def typecast_time(s: None | Literal[""]) -> None: ...  # type: ignore[overload-overlap]
@overload
def typecast_time(s: str) -> datetime.time: ...
@overload
def typecast_timestamp(s: None | Literal[""]) -> None: ...  # type: ignore[overload-overlap]
@overload
def typecast_timestamp(s: str) -> datetime.datetime: ...
def split_identifier(identifier: str) -> tuple[str, str]: ...
def truncate_name(identifier: str, length: int | None = None, hash_len: int = 4) -> str: ...
def format_number(value: Decimal | None, max_digits: int | None, decimal_places: int | None) -> str | None: ...
def strip_quotes(table_name: str) -> str: ...
