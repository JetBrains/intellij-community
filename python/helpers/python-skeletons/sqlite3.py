"""Skeleton for 'sqlite3' stdlib module."""


import sqlite3


def connect(database, timeout=5.0, detect_types=0, isolation_level=None,
            check_same_thread=False, factory=None, cached_statements=100):
    """Opens a connection to the SQLite database file database.

    :type database: bytes | unicode
    :type timeout: float
    :type detect_types: int
    :type isolation_level: string | None
    :type check_same_thread: bool
    :type factory: (() -> sqlite3.Connection) | None
    :rtype: sqlite3.Connection
    """
    return sqlite3.Connection()


def register_converter(typename, callable):
    """Registers a callable to convert a bytestring from the database into a
    custom Python type.

    :type typename: string
    :type callable: (bytes) -> unknown
    :rtype: None
    """
    pass


def register_adapter(type, callable):
    """Registers a callable to convert the custom Python type type into one of
    SQLite's supported types.

    :type type: type
    :type callable: (unknown) -> unknown
    :rtype: None
    """
    pass


def complete_statement(sql):
    """Returns True if the string sql contains one or more complete SQL
    statements terminated by semicolons.

    :type sql: string
    :rtype: bool
    """
    return False


def enable_callback_tracebacks(flag):
    """By default you will not get any tracebacks in user-defined functions,
    aggregates, converters, authorizer callbacks etc.

    :type flag: bool
    :rtype: None
    """
    pass


class Connection(object):
    """A SQLite database connection."""

    def cursor(self, cursorClass=None):
        """
        :type cursorClass: type | None
        :rtype: sqlite3.Cursor
        """
        return sqlite3.Cursor()

    def execute(self, sql, parameters=()):
        """This is a nonstandard shortcut that creates an intermediate cursor
        object by calling the cursor method, then calls the cursor's execute
        method with the parameters given.

        :type sql: string
        :type parameters: collections.Iterable
        :rtype: sqlite3.Cursor
        """
        pass

    def executemany(self, sql, seq_of_parameters=()):
        """This is a nonstandard shortcut that creates an intermediate cursor
        object by calling the cursor method, then calls the cursor's
        executemany method with the parameters given.

        :type sql: string
        :type seq_of_parameters: collections.Iterable[collections.Iterable]
        :rtype: sqlite3.Cursor
        """
        pass

    def executescript(self, sql_script):
        """This is a nonstandard shortcut that creates an intermediate cursor
        object by calling the cursor method, then calls the cursor's
        executescript method with the parameters given.

        :type sql_script: bytes | unicode
        :rtype: sqlite3.Cursor
        """
        pass

    def create_function(self, name, num_params, func):
        """Creates a user-defined function that you can later use from within
        SQL statements under the function name name.

        :type name: string
        :type num_params: int
        :type func: collections.Callable
        :rtype: None
        """
        pass


    def create_aggregate(self, name, num_params, aggregate_class):
        """Creates a user-defined aggregate function.

        :type name: string
        :type num_params: int
        :type aggregate_class: type
        :rtype: None
        """
        pass

    def create_collation(self, name, callable):
        """Creates a collation with the specified name and callable.

        :type name: string
        :type callable: collections.Callable
        :rtype: None
        """
        pass


class Cursor(object):
    """A SQLite database cursor."""

    def execute(self, sql, parameters=()):
        """Executes an SQL statement.

        :type sql: string
        :type parameters: collections.Iterable
        :rtype: sqlite3.Cursor
        """
        pass

    def executemany(self, sql, seq_of_parameters=()):
        """Executes an SQL command against all parameter sequences or mappings
        found in the sequence.

        :type sql: string
        :type seq_of_parameters: collections.Iterable[collections.Iterable]
        :rtype: sqlite3.Cursor
        """
        pass

    def executescript(self, sql_script):
        """This is a nonstandard convenience method for executing multiple SQL
        statements at once.

        :type sql_script: bytes | unicode
        :rtype: sqlite3.Cursor
        """
        pass

    def fetchone(self):
        """Fetches the next row of a query result set, returning a single
        sequence, or None when no more data is available.

        :rtype: tuple | None
        """
        pass

    def fetchmany(self, size=-1):
        """Fetches the next set of rows of a query result, returning a list.

        :type size: numbers.Integral
        :rtype: list[tuple]
        """
        return []

    def fetchall(self):
        """Fetches all (remaining) rows of a query result, returning a list.

        :rtype: list[tuple]
        """
        return []
