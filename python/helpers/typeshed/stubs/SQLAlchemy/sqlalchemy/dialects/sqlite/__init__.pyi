#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any

from .base import (
    BLOB as BLOB,
    BOOLEAN as BOOLEAN,
    CHAR as CHAR,
    DATE as DATE,
    DATETIME as DATETIME,
    DECIMAL as DECIMAL,
    FLOAT as FLOAT,
    INTEGER as INTEGER,
    JSON as JSON,
    NUMERIC as NUMERIC,
    REAL as REAL,
    SMALLINT as SMALLINT,
    TEXT as TEXT,
    TIME as TIME,
    TIMESTAMP as TIMESTAMP,
    VARCHAR as VARCHAR,
)
from .dml import Insert as Insert, insert as insert

__all__ = (
    "BLOB",
    "BOOLEAN",
    "CHAR",
    "DATE",
    "DATETIME",
    "DECIMAL",
    "FLOAT",
    "INTEGER",
    "JSON",
    "NUMERIC",
    "SMALLINT",
    "TEXT",
    "TIME",
    "TIMESTAMP",
    "VARCHAR",
    "REAL",
    "Insert",
    "insert",
    "dialect",
)

dialect: Any
