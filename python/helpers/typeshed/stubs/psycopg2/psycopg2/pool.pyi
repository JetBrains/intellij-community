from typing import Any

import psycopg2

class PoolError(psycopg2.Error): ...

class AbstractConnectionPool:
    minconn: Any
    maxconn: Any
    closed: bool
    def __init__(self, minconn, maxconn, *args, **kwargs) -> None: ...

class SimpleConnectionPool(AbstractConnectionPool):
    getconn: Any
    putconn: Any
    closeall: Any

class ThreadedConnectionPool(AbstractConnectionPool):
    def __init__(self, minconn, maxconn, *args, **kwargs) -> None: ...
    def getconn(self, key: Any | None = ...): ...
    def putconn(self, conn: Any | None = ..., key: Any | None = ..., close: bool = ...) -> None: ...
    def closeall(self) -> None: ...
