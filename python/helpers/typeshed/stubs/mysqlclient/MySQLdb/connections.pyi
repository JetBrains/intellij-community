from _typeshed import Self
from types import TracebackType
from typing import Any

from . import _mysql, cursors
from ._exceptions import (
    DatabaseError as DatabaseError,
    DataError as DataError,
    Error as Error,
    IntegrityError as IntegrityError,
    InterfaceError as InterfaceError,
    InternalError as InternalError,
    NotSupportedError as NotSupportedError,
    OperationalError as OperationalError,
    ProgrammingError as ProgrammingError,
    Warning as Warning,
)

re_numeric_part: Any

def numeric_part(s): ...

class Connection(_mysql.connection):
    default_cursor: type[cursors.Cursor]
    cursorclass: type[cursors.BaseCursor]
    encoders: Any
    encoding: str
    messages: Any
    def __init__(self, *args, **kwargs) -> None: ...
    def __enter__(self: Self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_value: BaseException | None, traceback: TracebackType | None
    ) -> None: ...
    def autocommit(self, on: bool) -> None: ...
    def cursor(self, cursorclass: type[cursors.BaseCursor] | None = ...): ...
    def query(self, query) -> None: ...
    def literal(self, o): ...
    def begin(self) -> None: ...
    def warning_count(self): ...
    def set_character_set(self, charset) -> None: ...
    def set_sql_mode(self, sql_mode) -> None: ...
    def show_warnings(self): ...
    Warning: type[BaseException]
    Error: type[BaseException]
    InterfaceError: type[BaseException]
    DatabaseError: type[BaseException]
    DataError: type[BaseException]
    OperationalError: type[BaseException]
    IntegrityError: type[BaseException]
    InternalError: type[BaseException]
    ProgrammingError: type[BaseException]
    NotSupportedError: type[BaseException]
