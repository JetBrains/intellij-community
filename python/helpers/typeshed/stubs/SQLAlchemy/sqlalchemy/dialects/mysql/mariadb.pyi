#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any

from .base import MySQLDialect

class MariaDBDialect(MySQLDialect):
    is_mariadb: bool
    supports_statement_cache: bool
    name: str
    preparer: Any

def loader(driver): ...
