from django.db.backends.base.features import BaseDatabaseFeatures
from django.db.backends.sqlite3.base import DatabaseWrapper
from django.utils.functional import cached_property
from typing_extensions import override

class DatabaseFeatures(BaseDatabaseFeatures):
    connection: DatabaseWrapper
    can_alter_table_drop_column: bool
    supports_aggregate_order_by_clause: bool
    supports_aggregate_distinct_multiple_argument: bool
    supports_any_value: bool
    @cached_property
    @override
    def django_test_skips(self) -> dict[str, set[str]]: ...  # type: ignore[override]
    @cached_property
    @override
    def introspected_field_types(self) -> dict[str, str]: ...  # type: ignore[override]
    @property
    @override
    def max_query_params(self) -> int: ...  # type: ignore[override]
    @cached_property
    @override
    def supports_json_field(self) -> bool: ...  # type: ignore[override]
    @property
    @override
    def can_introspect_json_field(self) -> bool: ...  # type: ignore[override]
    @property
    @override
    def has_json_object_function(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def can_return_columns_from_insert(self) -> bool: ...  # type: ignore[override]
    @property
    @override
    def can_return_rows_from_bulk_insert(self) -> bool: ...  # type: ignore[override]
    @property
    @override
    def can_return_rows_from_update(self) -> bool: ...  # type: ignore[override]
