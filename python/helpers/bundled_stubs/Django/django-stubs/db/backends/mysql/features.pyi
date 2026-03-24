from typing import Any

from django.db.backends.base.features import BaseDatabaseFeatures
from django.db.backends.mysql.base import DatabaseWrapper
from django.utils.functional import cached_property
from typing_extensions import override

class DatabaseFeatures(BaseDatabaseFeatures):
    connection: DatabaseWrapper
    empty_fetchmany_value: Any
    related_fields_match_type: bool
    allow_sliced_subqueries_with_in: bool
    has_select_for_update: bool
    has_select_for_update_nowait: bool
    has_select_for_update_skip_locked: bool
    supports_forward_references: bool
    supports_regex_backreferencing: bool
    supports_date_lookup_using_string: bool
    supports_timezones: bool
    requires_explicit_null_ordering_when_grouping: bool
    atomic_transactions: bool
    can_clone_databases: bool
    supports_temporal_subtraction: bool
    supports_slicing_ordering_in_compound: bool
    supports_index_on_text_field: bool
    supports_over_clause: bool
    supports_frame_range_fixed_distance: bool
    create_test_procedure_without_params_sql: str
    create_test_procedure_with_int_param_sql: str
    supports_partial_indexes: bool
    supports_order_by_nulls_modifier: bool
    order_by_nulls_first: bool
    supports_aggregate_order_by_clause: bool
    supports_json_negative_indexing: bool

    @cached_property
    @override
    def minimum_database_version(self) -> tuple[int, ...]: ...  # type: ignore[override]
    @cached_property
    @override
    def test_collations(self) -> dict[str, str]: ...  # type: ignore[override]
    @cached_property
    @override
    def django_test_skips(self) -> dict[str, set[str]]: ...  # type: ignore[override]
    @cached_property
    @override
    def allows_auto_pk_0(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def update_can_self_select(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def can_introspect_foreign_keys(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def introspected_field_types(self) -> dict[str, str]: ...  # type: ignore[override]
    @cached_property
    @override
    def can_return_columns_from_insert(self) -> bool: ...  # type: ignore[override]
    @property
    @override
    def can_return_rows_from_bulk_insert(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def has_zoneinfo_database(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def is_sql_auto_is_null_enabled(self) -> bool: ...
    @cached_property
    @override
    def supports_column_check_constraints(self) -> bool: ...  # type: ignore[override]
    @property
    @override
    def supports_table_check_constraints(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def can_introspect_check_constraints(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def has_select_for_update_of(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def supports_explain_analyze(self) -> bool: ...
    @cached_property
    @override
    def supported_explain_formats(self) -> set[str]: ...  # type: ignore[override]
    @cached_property
    @override
    def supports_transactions(self) -> bool: ...
    @cached_property
    @override
    def ignores_table_name_case(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def supports_default_in_lead_lag(self) -> bool: ...  # type: ignore[override]
    @property
    @override
    def supports_json_field(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def can_introspect_json_field(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def supports_index_column_ordering(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def supports_expression_indexes(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def supports_select_intersection(self) -> bool: ...  # type: ignore[override]
    @property
    @override
    def supports_select_difference(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def supports_expression_defaults(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def has_native_uuid_field(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def allows_group_by_selected_pks(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def supports_any_value(self) -> bool: ...  # type: ignore[override]
