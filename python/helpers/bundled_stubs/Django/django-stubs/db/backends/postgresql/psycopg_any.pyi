from enum import IntEnum
from types import ModuleType
from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper

is_psycopg3: bool

TSRANGE_OID: int
TSTZRANGE_OID: int

errors: ModuleType
sql: ModuleType
adapt: ModuleType
adapters: Any

class IsolationLevel(IntEnum):
    READ_UNCOMMITTED = 1
    READ_COMMITTED = 2
    REPEATABLE_READ = 3
    SERIALIZABLE = 4

# Range types from psycopg
Range: Any
DateRange: Any
DateTimeRange: Any
DateTimeTZRange: Any
NumericRange: Any
RANGE_TYPES: tuple[Any, ...]

# Psycopg types
Jsonb: Any
ClientCursor: Any
TextLoader: Any
TimestamptzLoader: Any
BaseTzLoader: Any
RangeDumper: Any
DjangoRangeDumper: Any

def Inet(address: str) -> Any: ...
def mogrify(sql: str, params: Any, connection: BaseDatabaseWrapper) -> str: ...
def register_tzloader(tz: Any, context: Any) -> None: ...
def get_adapters_template(use_tz: bool, timezone: Any) -> Any: ...
