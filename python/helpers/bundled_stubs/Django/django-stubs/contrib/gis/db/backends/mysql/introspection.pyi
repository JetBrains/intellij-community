from typing import Any

from django.db.backends.mysql.introspection import DatabaseIntrospection

class MySQLIntrospection(DatabaseIntrospection):
    data_types_reverse: Any
    def get_geometry_type(self, table_name: Any, description: Any) -> Any: ...
    def supports_spatial_index(self, cursor: Any, table_name: Any) -> Any: ...
