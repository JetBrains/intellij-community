from __future__ import annotations

import io
from typing_extensions import assert_type

import psycopg2.extensions
import psycopg2.extras
from psycopg2.extensions import make_dsn

# make_dsn
# --------

# (None) -> str
assert_type(make_dsn(), str)
assert_type(make_dsn(None), str)
assert_type(make_dsn(dsn=None), str)

# (bytes) -> bytes
assert_type(make_dsn(b""), bytes)
assert_type(make_dsn(dsn=b""), bytes)

# (bytes, **Kwargs) -> str
assert_type(make_dsn(b"", database=""), str)
assert_type(make_dsn(dsn=b"", database=""), str)

# (str, **OptionalKwargs) -> str
assert_type(make_dsn(""), str)
assert_type(make_dsn(dsn=""), str)
assert_type(make_dsn("", database=None), str)
assert_type(make_dsn(dsn="", database=None), str)


# connection.cursor
# -----------------

# (name?, None?, ...) -> psycopg2.extensions.cursor
conn = psycopg2.connect("test-conn")
assert_type(conn.cursor(), psycopg2.extensions.cursor)
assert_type(conn.cursor("test-cur"), psycopg2.extensions.cursor)
assert_type(conn.cursor("test-cur", None), psycopg2.extensions.cursor)
assert_type(conn.cursor("test-cur", cursor_factory=None), psycopg2.extensions.cursor)


# (name?, cursor_factory(), ...) -> custom_cursor
class MyCursor(psycopg2.extensions.cursor):
    pass


assert_type(conn.cursor("test-cur", cursor_factory=MyCursor), MyCursor)
assert_type(conn.cursor("test-cur", cursor_factory=lambda c, n: MyCursor(c, n)), MyCursor)

dconn = psycopg2.extras.DictConnection("test-dconn")
assert_type(dconn.cursor(), psycopg2.extras.DictCursor)
assert_type(dconn.cursor("test-dcur"), psycopg2.extras.DictCursor)
assert_type(dconn.cursor("test-dcur", None), psycopg2.extras.DictCursor)
assert_type(dconn.cursor("test-dcur", cursor_factory=None), psycopg2.extras.DictCursor)
assert_type(dconn.cursor("test-dcur", cursor_factory=MyCursor), MyCursor)

# file protocols
# --------------
cur = conn.cursor()
cur.copy_from(io.StringIO(), "table")
