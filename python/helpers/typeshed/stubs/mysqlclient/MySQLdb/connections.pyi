from typing import Any

from . import _mysql, cursors as cursors
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
    default_cursor: Any
    cursorclass: Any
    encoders: Any
    encoding: str
    messages: Any
    def __init__(self, *args, **kwargs): ...
    def __enter__(self): ...
    def __exit__(self, exc_type, exc_value, traceback) -> None: ...
    def autocommit(self, on) -> None: ...
    def cursor(self, cursorclass: Any | None = ...): ...
    def query(self, query) -> None: ...
    def literal(self, o): ...
    def begin(self) -> None: ...
    def warning_count(self): ...
    def set_character_set(self, charset) -> None: ...
    def set_sql_mode(self, sql_mode) -> None: ...
    def show_warnings(self): ...
    Warning: Any
    Error: Any
    InterfaceError: Any
    DatabaseError: Any
    DataError: Any
    OperationalError: Any
    IntegrityError: Any
    InternalError: Any
    ProgrammingError: Any
    NotSupportedError: Any
