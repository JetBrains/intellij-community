from typing import Any

from django.db.backends.oracle.introspection import DatabaseIntrospection

class OracleIntrospection(DatabaseIntrospection):
    @property
    def data_types_reverse(self) -> Any: ...
    def get_geometry_type(self, table_name: Any, description: Any) -> Any: ...
