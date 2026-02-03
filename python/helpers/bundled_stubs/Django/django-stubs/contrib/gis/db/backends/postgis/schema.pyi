from typing import Any

from django.db.backends.postgresql.schema import DatabaseSchemaEditor

class PostGISSchemaEditor(DatabaseSchemaEditor):
    geom_index_type: str
    geom_index_ops_nd: str
    rast_index_wrapper: str
    sql_alter_column_to_3d: str
    sql_alter_column_to_2d: str
    def geo_quote_name(self, name: Any) -> Any: ...
