from typing import Any

from django.db.backends.oracle.base import DatabaseWrapper as OracleDatabaseWrapper

class DatabaseWrapper(OracleDatabaseWrapper):
    SchemaEditorClass: Any
    features_class: Any
    introspection_class: Any
    ops_class: Any
