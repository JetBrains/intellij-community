from typing import Any

# connection and cursor not available at runtime
from psycopg2._psycopg import (
    BINARY as BINARY,
    DATETIME as DATETIME,
    NUMBER as NUMBER,
    ROWID as ROWID,
    STRING as STRING,
    Binary as Binary,
    DatabaseError as DatabaseError,
    DataError as DataError,
    Date as Date,
    DateFromTicks as DateFromTicks,
    Error as Error,
    IntegrityError as IntegrityError,
    InterfaceError as InterfaceError,
    InternalError as InternalError,
    NotSupportedError as NotSupportedError,
    OperationalError as OperationalError,
    ProgrammingError as ProgrammingError,
    Time as Time,
    TimeFromTicks as TimeFromTicks,
    Timestamp as Timestamp,
    TimestampFromTicks as TimestampFromTicks,
    Warning as Warning,
    __libpq_version__ as __libpq_version__,
    apilevel as apilevel,
    connection as connection,
    cursor as cursor,
    paramstyle as paramstyle,
    threadsafety as threadsafety,
)

def connect(
    dsn: Any | None = ..., connection_factory: Any | None = ..., cursor_factory: Any | None = ..., **kwargs
) -> connection: ...
