from typing import Union, Tuple, Callable
from .connections import Connection
from .constants import FIELD_TYPE as FIELD_TYPE
from .converters import escape_dict as escape_dict, escape_sequence as escape_sequence, escape_string as escape_string
from .err import Warning as Warning, Error as Error, InterfaceError as InterfaceError, DataError as DataError, DatabaseError as DatabaseError, OperationalError as OperationalError, IntegrityError as IntegrityError, InternalError as InternalError, NotSupportedError as NotSupportedError, ProgrammingError as ProgrammingError, MySQLError as MySQLError
from .times import Date as Date, Time as Time, Timestamp as Timestamp, DateFromTicks as DateFromTicks, TimeFromTicks as TimeFromTicks, TimestampFromTicks as TimestampFromTicks

threadsafety = ...  # type: int
apilevel = ...  # type: str
paramstyle = ...  # type: str

class DBAPISet(frozenset):
    def __ne__(self, other) -> bool: ...
    def __eq__(self, other) -> bool: ...
    def __hash__(self) -> int: ...

STRING = ...  # type: DBAPISet
BINARY = ...  # type: DBAPISet
NUMBER = ...  # type: DBAPISet
DATE = ...  # type: DBAPISet
TIME = ...  # type: DBAPISet
TIMESTAMP = ...  # type: DBAPISet
ROWID = ...  # type: DBAPISet

def Binary(x) -> Union[bytearray, bytes]: ...
def Connect(*args, **kwargs) -> Connection: ...
def get_client_info() -> str: ...

connect = ...  # type: Callable[..., Connection]


version_info = ...  # type: Tuple[int, int, int, str, int]
NULL = ...  # type: str

def install_as_MySQLdb() -> None: ...
