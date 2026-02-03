from django.db.backends.base.creation import BaseDatabaseCreation
from django.db.backends.mysql.base import DatabaseWrapper

class DatabaseCreation(BaseDatabaseCreation):
    connection: DatabaseWrapper
    def sql_table_creation_suffix(self) -> str: ...
