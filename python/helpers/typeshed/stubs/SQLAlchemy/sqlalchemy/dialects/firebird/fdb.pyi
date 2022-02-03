from .kinterbasdb import FBDialect_kinterbasdb

class FBDialect_fdb(FBDialect_kinterbasdb):
    supports_statement_cache: bool
    def __init__(self, enable_rowcount: bool = ..., retaining: bool = ..., **kwargs) -> None: ...
    @classmethod
    def dbapi(cls): ...
    def create_connect_args(self, url): ...

dialect = FBDialect_fdb
