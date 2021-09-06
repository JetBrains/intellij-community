from typing import Any, FrozenSet

from MySQLdb import connections as connections, constants as constants, converters as converters, cursors as cursors
from MySQLdb._mysql import (
    DatabaseError as DatabaseError,
    DataError as DataError,
    Error as Error,
    IntegrityError as IntegrityError,
    InterfaceError as InterfaceError,
    InternalError as InternalError,
    MySQLError as MySQLError,
    NotSupportedError as NotSupportedError,
    OperationalError as OperationalError,
    ProgrammingError as ProgrammingError,
    Warning as Warning,
    debug as debug,
    escape as escape,
    escape_string as escape_string,
    get_client_info as get_client_info,
    string_literal as string_literal,
)
from MySQLdb.connections import Connection as Connection
from MySQLdb.constants import FIELD_TYPE as FIELD_TYPE
from MySQLdb.release import version_info as version_info
from MySQLdb.times import (
    Date as Date,
    DateFromTicks as DateFromTicks,
    Time as Time,
    TimeFromTicks as TimeFromTicks,
    Timestamp as Timestamp,
    TimestampFromTicks as TimestampFromTicks,
)

threadsafety: int
apilevel: str
paramstyle: str

class DBAPISet(FrozenSet[Any]):
    def __eq__(self, other): ...

STRING: Any
BINARY: Any
NUMBER: Any
DATE: Any
TIME: Any
TIMESTAMP: Any
DATETIME: Any
ROWID: Any

def Binary(x): ...
def Connect(*args, **kwargs): ...

connect = Connect
