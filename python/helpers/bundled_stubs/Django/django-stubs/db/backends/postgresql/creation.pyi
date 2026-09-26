from django.db.backends.base.creation import BaseDatabaseCreation
from django.db.backends.postgresql.base import DatabaseWrapper

class DatabaseCreation(BaseDatabaseCreation):
    connection: DatabaseWrapper
