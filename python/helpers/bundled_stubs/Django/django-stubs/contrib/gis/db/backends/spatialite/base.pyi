from typing import Any

from django.db.backends.sqlite3.base import DatabaseWrapper as SQLiteDatabaseWrapper

from .client import SpatiaLiteClient
from .features import DatabaseFeatures
from .introspection import SpatiaLiteIntrospection
from .operations import SpatiaLiteOperations
from .schema import SpatialiteSchemaEditor

class DatabaseWrapper(SQLiteDatabaseWrapper):
    SchemaEditorClass: type[SpatialiteSchemaEditor]
    client_class: type[SpatiaLiteClient]
    features_class: type[DatabaseFeatures]
    introspection_class: type[SpatiaLiteIntrospection]
    ops_class: type[SpatiaLiteOperations]
    lib_spatialite_paths: str
    ops: SpatiaLiteOperations
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def get_new_connection(self, conn_params: Any) -> Any: ...
    def prepare_database(self) -> None: ...
