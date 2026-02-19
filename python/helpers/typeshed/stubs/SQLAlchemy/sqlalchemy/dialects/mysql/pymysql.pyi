#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any

from ...util import memoized_property
from .mysqldb import MySQLDialect_mysqldb

class MySQLDialect_pymysql(MySQLDialect_mysqldb):
    driver: str
    supports_statement_cache: bool
    description_encoding: Any
    supports_unicode_statements: bool
    supports_unicode_binds: bool
    @memoized_property
    def supports_server_side_cursors(self): ...
    @classmethod
    def dbapi(cls): ...
    def create_connect_args(self, url, _translate_args: Any | None = ...): ...
    def is_disconnect(self, e, connection, cursor): ...

dialect = MySQLDialect_pymysql
