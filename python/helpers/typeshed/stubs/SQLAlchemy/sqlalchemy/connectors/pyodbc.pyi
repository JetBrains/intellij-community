from typing import Any

from . import Connector

class PyODBCConnector(Connector):
    driver: str
    supports_sane_rowcount_returning: bool
    supports_sane_multi_rowcount: bool
    supports_unicode_statements: bool
    supports_unicode_binds: bool
    supports_native_decimal: bool
    default_paramstyle: str
    use_setinputsizes: bool
    pyodbc_driver_name: Any
    def __init__(self, supports_unicode_binds: Any | None = ..., use_setinputsizes: bool = ..., **kw) -> None: ...
    @classmethod
    def dbapi(cls): ...
    def create_connect_args(self, url): ...
    def is_disconnect(self, e, connection, cursor): ...
    def do_set_input_sizes(self, cursor, list_of_tuples, context) -> None: ...
    def set_isolation_level(self, connection, level) -> None: ...
