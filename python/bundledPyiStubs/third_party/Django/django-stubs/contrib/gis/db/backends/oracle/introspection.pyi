from functools import cached_property
from typing import Any

from django.db.backends.oracle.introspection import DatabaseIntrospection
from typing_extensions import override

class OracleIntrospection(DatabaseIntrospection):
    @cached_property
    @override
    def data_types_reverse(self) -> dict[int, str]: ...  # type: ignore[override]
    def get_geometry_type(self, table_name: Any, description: Any) -> Any: ...
