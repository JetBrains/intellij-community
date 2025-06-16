from typing import Any

from django.db.backends.mysql.base import DatabaseWrapper as MySQLDatabaseWrapper

class DatabaseWrapper(MySQLDatabaseWrapper):
    SchemaEditorClass: Any
    features_class: Any
    introspection_class: Any
    ops_class: Any
