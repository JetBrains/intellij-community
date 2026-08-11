from _typeshed import StrPath
from django.db.backends.base.creation import BaseDatabaseCreation
from django.db.backends.sqlite3.base import DatabaseWrapper

class DatabaseCreation(BaseDatabaseCreation):
    connection: DatabaseWrapper

    @staticmethod
    def is_in_memory_db(database_name: StrPath) -> bool: ...
