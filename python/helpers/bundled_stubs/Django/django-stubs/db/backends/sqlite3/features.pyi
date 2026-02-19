from django.db.backends.base.features import BaseDatabaseFeatures
from django.db.backends.sqlite3.base import DatabaseWrapper

class DatabaseFeatures(BaseDatabaseFeatures):
    connection: DatabaseWrapper
