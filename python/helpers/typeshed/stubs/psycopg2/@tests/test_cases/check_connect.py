from __future__ import annotations

from typing_extensions import assert_type

import psycopg2
from psycopg2.extensions import connection, cursor


class MyCursor(cursor):
    pass


class MyConnection(connection):
    pass


def custom_connection(dsn: str) -> MyConnection:
    return MyConnection(dsn, async_=0)


# -> psycopg2.extensions.connection
assert_type(psycopg2.connect(), connection)
assert_type(psycopg2.connect("test-conn"), connection)
assert_type(psycopg2.connect(None), connection)
assert_type(psycopg2.connect("test-conn", connection_factory=None), connection)

assert_type(psycopg2.connect(cursor_factory=MyCursor), connection)
assert_type(psycopg2.connect("test-conn", cursor_factory=MyCursor), connection)
assert_type(psycopg2.connect(None, cursor_factory=MyCursor), connection)
assert_type(psycopg2.connect("test-conn", connection_factory=None, cursor_factory=MyCursor), connection)

# -> custom_connection
assert_type(psycopg2.connect(connection_factory=MyConnection), MyConnection)
assert_type(psycopg2.connect("test-conn", connection_factory=MyConnection), MyConnection)
assert_type(psycopg2.connect("test-conn", MyConnection), MyConnection)
assert_type(psycopg2.connect(connection_factory=custom_connection), MyConnection)

assert_type(psycopg2.connect(connection_factory=MyConnection, cursor_factory=MyCursor), MyConnection)
assert_type(psycopg2.connect("test-conn", connection_factory=MyConnection, cursor_factory=MyCursor), MyConnection)
assert_type(psycopg2.connect(connection_factory=custom_connection, cursor_factory=MyCursor), MyConnection)
