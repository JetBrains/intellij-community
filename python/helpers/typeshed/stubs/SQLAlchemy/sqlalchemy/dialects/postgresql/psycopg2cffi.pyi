#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any

from .psycopg2 import PGDialect_psycopg2

class PGDialect_psycopg2cffi(PGDialect_psycopg2):
    driver: str
    supports_unicode_statements: bool
    supports_statement_cache: bool
    FEATURE_VERSION_MAP: Any
    @classmethod
    def dbapi(cls): ...

dialect = PGDialect_psycopg2cffi
