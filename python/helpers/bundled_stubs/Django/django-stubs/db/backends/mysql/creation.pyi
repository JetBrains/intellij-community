from django.db.backends.base.creation import BaseDatabaseCreation
from django.db.backends.mysql.base import DatabaseWrapper
from typing_extensions import override

class DatabaseCreation(BaseDatabaseCreation):
    connection: DatabaseWrapper
    @override
    def sql_table_creation_suffix(self) -> str: ...
