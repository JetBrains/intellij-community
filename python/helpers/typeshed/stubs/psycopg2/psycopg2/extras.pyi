from _typeshed import Incomplete
from collections import OrderedDict
from collections.abc import Callable
from typing import Any, NamedTuple, TypeVar, overload

from psycopg2._ipaddress import register_ipaddress as register_ipaddress
from psycopg2._json import (
    Json as Json,
    register_default_json as register_default_json,
    register_default_jsonb as register_default_jsonb,
    register_json as register_json,
)
from psycopg2._psycopg import (
    REPLICATION_LOGICAL as REPLICATION_LOGICAL,
    REPLICATION_PHYSICAL as REPLICATION_PHYSICAL,
    ReplicationConnection as _replicationConnection,
    ReplicationCursor as _replicationCursor,
    ReplicationMessage as ReplicationMessage,
    connection as _connection,
    cursor as _cursor,
    quote_ident as quote_ident,
)
from psycopg2._range import (
    DateRange as DateRange,
    DateTimeRange as DateTimeRange,
    DateTimeTZRange as DateTimeTZRange,
    NumericRange as NumericRange,
    Range as Range,
    RangeAdapter as RangeAdapter,
    RangeCaster as RangeCaster,
    register_range as register_range,
)

_T_cur = TypeVar("_T_cur", bound=_cursor)

class DictCursorBase(_cursor):
    def __init__(self, *args, **kwargs) -> None: ...

class DictConnection(_connection):
    @overload
    def cursor(
        self, name: str | bytes | None = None, cursor_factory: None = None, withhold: bool = False, scrollable: bool | None = None
    ) -> DictCursor: ...
    @overload
    def cursor(
        self,
        name: str | bytes | None = None,
        *,
        cursor_factory: Callable[..., _T_cur],
        withhold: bool = False,
        scrollable: bool | None = None,
    ) -> _T_cur: ...
    @overload
    def cursor(
        self,
        name: str | bytes | None,
        cursor_factory: Callable[..., _T_cur],
        withhold: bool = False,
        scrollable: bool | None = None,
    ) -> _T_cur: ...

class DictCursor(DictCursorBase):
    def __init__(self, *args, **kwargs) -> None: ...
    index: Any
    def execute(self, query, vars: Incomplete | None = None): ...
    def callproc(self, procname, vars: Incomplete | None = None): ...
    def fetchone(self) -> DictRow | None: ...  # type: ignore[override]
    def fetchmany(self, size: int | None = None) -> list[DictRow]: ...  # type: ignore[override]
    def fetchall(self) -> list[DictRow]: ...  # type: ignore[override]
    def __next__(self) -> DictRow: ...  # type: ignore[override]

class DictRow(list[Any]):
    def __init__(self, cursor) -> None: ...
    def __getitem__(self, x): ...
    def __setitem__(self, x, v) -> None: ...
    def items(self): ...
    def keys(self): ...
    def values(self): ...
    def get(self, x, default: Incomplete | None = None): ...
    def copy(self): ...
    def __contains__(self, x): ...
    def __reduce__(self): ...

class RealDictConnection(_connection):
    @overload
    def cursor(
        self, name: str | bytes | None = None, cursor_factory: None = None, withhold: bool = False, scrollable: bool | None = None
    ) -> RealDictCursor: ...
    @overload
    def cursor(
        self,
        name: str | bytes | None = None,
        *,
        cursor_factory: Callable[..., _T_cur],
        withhold: bool = False,
        scrollable: bool | None = None,
    ) -> _T_cur: ...
    @overload
    def cursor(
        self,
        name: str | bytes | None,
        cursor_factory: Callable[..., _T_cur],
        withhold: bool = False,
        scrollable: bool | None = None,
    ) -> _T_cur: ...

class RealDictCursor(DictCursorBase):
    def __init__(self, *args, **kwargs) -> None: ...
    column_mapping: Any
    def execute(self, query, vars: Incomplete | None = None): ...
    def callproc(self, procname, vars: Incomplete | None = None): ...
    def fetchone(self) -> RealDictRow | None: ...  # type: ignore[override]
    def fetchmany(self, size: int | None = None) -> list[RealDictRow]: ...  # type: ignore[override]
    def fetchall(self) -> list[RealDictRow]: ...  # type: ignore[override]
    def __next__(self) -> RealDictRow: ...  # type: ignore[override]

class RealDictRow(OrderedDict[Any, Any]):
    def __init__(self, *args, **kwargs) -> None: ...
    def __setitem__(self, key, value) -> None: ...

class NamedTupleConnection(_connection):
    @overload
    def cursor(
        self, name: str | bytes | None = None, cursor_factory: None = None, withhold: bool = False, scrollable: bool | None = None
    ) -> NamedTupleCursor: ...
    @overload
    def cursor(
        self,
        name: str | bytes | None = None,
        *,
        cursor_factory: Callable[..., _T_cur],
        withhold: bool = False,
        scrollable: bool | None = None,
    ) -> _T_cur: ...
    @overload
    def cursor(
        self,
        name: str | bytes | None,
        cursor_factory: Callable[..., _T_cur],
        withhold: bool = False,
        scrollable: bool | None = None,
    ) -> _T_cur: ...

class NamedTupleCursor(_cursor):
    Record: Any
    MAX_CACHE: int
    def execute(self, query, vars: Incomplete | None = None): ...
    def executemany(self, query, vars): ...
    def callproc(self, procname, vars: Incomplete | None = None): ...
    def fetchone(self) -> NamedTuple | None: ...
    def fetchmany(self, size: int | None = None) -> list[NamedTuple]: ...  # type: ignore[override]
    def fetchall(self) -> list[NamedTuple]: ...  # type: ignore[override]
    def __next__(self) -> NamedTuple: ...

class LoggingConnection(_connection):
    log: Any
    def initialize(self, logobj) -> None: ...
    def filter(self, msg, curs): ...
    def cursor(self, *args, **kwargs): ...

class LoggingCursor(_cursor):
    def execute(self, query, vars: Incomplete | None = None): ...
    def callproc(self, procname, vars: Incomplete | None = None): ...

class MinTimeLoggingConnection(LoggingConnection):
    def initialize(self, logobj, mintime: int = 0) -> None: ...
    def filter(self, msg, curs): ...
    def cursor(self, *args, **kwargs): ...

class MinTimeLoggingCursor(LoggingCursor):
    timestamp: Any
    def execute(self, query, vars: Incomplete | None = None): ...
    def callproc(self, procname, vars: Incomplete | None = None): ...

class LogicalReplicationConnection(_replicationConnection):
    def __init__(self, *args, **kwargs) -> None: ...

class PhysicalReplicationConnection(_replicationConnection):
    def __init__(self, *args, **kwargs) -> None: ...

class StopReplication(Exception): ...

class ReplicationCursor(_replicationCursor):
    def create_replication_slot(
        self, slot_name, slot_type: Incomplete | None = None, output_plugin: Incomplete | None = None
    ) -> None: ...
    def drop_replication_slot(self, slot_name) -> None: ...
    def start_replication(
        self,
        slot_name: Incomplete | None = None,
        slot_type: Incomplete | None = None,
        start_lsn: int = 0,
        timeline: int = 0,
        options: Incomplete | None = None,
        decode: bool = False,
        status_interval: int = 10,
    ) -> None: ...
    def fileno(self): ...

class UUID_adapter:
    def __init__(self, uuid) -> None: ...
    def __conform__(self, proto): ...
    def getquoted(self): ...

def register_uuid(oids: Incomplete | None = None, conn_or_curs: Incomplete | None = None): ...

class Inet:
    addr: Any
    def __init__(self, addr) -> None: ...
    def prepare(self, conn) -> None: ...
    def getquoted(self): ...
    def __conform__(self, proto): ...

def register_inet(oid: Incomplete | None = None, conn_or_curs: Incomplete | None = None): ...
def wait_select(conn) -> None: ...

class HstoreAdapter:
    wrapped: Any
    def __init__(self, wrapped) -> None: ...
    conn: Any
    getquoted: Any
    def prepare(self, conn) -> None: ...
    @classmethod
    def parse(cls, s, cur, _bsdec=...): ...
    @classmethod
    def parse_unicode(cls, s, cur): ...
    @classmethod
    def get_oids(cls, conn_or_curs): ...

def register_hstore(
    conn_or_curs,
    globally: bool = False,
    unicode: bool = False,
    oid: Incomplete | None = None,
    array_oid: Incomplete | None = None,
) -> None: ...

class CompositeCaster:
    name: Any
    schema: Any
    oid: Any
    array_oid: Any
    attnames: Any
    atttypes: Any
    typecaster: Any
    array_typecaster: Any
    def __init__(self, name, oid, attrs, array_oid: Incomplete | None = None, schema: Incomplete | None = None) -> None: ...
    def parse(self, s, curs): ...
    def make(self, values): ...
    @classmethod
    def tokenize(cls, s): ...

def register_composite(name, conn_or_curs, globally: bool = False, factory: Incomplete | None = None): ...
def execute_batch(cur, sql, argslist, page_size: int = 100) -> None: ...
def execute_values(cur, sql, argslist, template: Incomplete | None = None, page_size: int = 100, fetch: bool = False): ...
