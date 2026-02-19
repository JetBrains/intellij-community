from typing import Any

from django.db.backends.base.features import BaseDatabaseFeatures
from django.db.backends.mysql.base import DatabaseWrapper
from django.utils.functional import cached_property

class DatabaseFeatures(BaseDatabaseFeatures):
    connection: DatabaseWrapper
    empty_fetchmany_value: Any
    allows_group_by_pk: bool
    related_fields_match_type: bool
    allow_sliced_subqueries_with_in: bool
    has_select_for_update: bool
    supports_forward_references: bool
    supports_regex_backreferencing: bool
    supports_date_lookup_using_string: bool
    can_introspect_autofield: bool
    can_introspect_binary_field: bool
    can_introspect_duration_field: bool
    can_introspect_small_integer_field: bool
    can_introspect_positive_integer_field: bool
    introspected_boolean_field_type: str
    supports_timezones: bool
    requires_explicit_null_ordering_when_grouping: bool
    can_release_savepoints: bool
    atomic_transactions: bool
    can_clone_databases: bool
    supports_temporal_subtraction: bool
    supports_select_intersection: bool
    supports_select_difference: bool
    supports_slicing_ordering_in_compound: bool
    supports_index_on_text_field: bool
    has_case_insensitive_like: bool
    create_test_procedure_without_params_sql: str
    create_test_procedure_with_int_param_sql: str
    db_functions_convert_bytes_to_str: bool
    supports_partial_indexes: bool
    supports_order_by_nulls_modifier: bool
    order_by_nulls_first: bool

    # fake properties (until `property` is generic)
    supports_frame_range_fixed_distance: bool
    supports_table_check_constraints: bool
    can_return_rows_from_bulk_insert: bool
    @cached_property
    def allows_auto_pk_0(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def update_can_self_select(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def can_introspect_foreign_keys(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def introspected_field_types(self) -> dict[str, str]: ...  # type: ignore[override]
    @cached_property
    def can_return_columns_from_insert(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def has_zoneinfo_database(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def is_sql_auto_is_null_enabled(self) -> bool: ...
    @cached_property
    def supports_over_clause(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def supports_column_check_constraints(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def can_introspect_check_constraints(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def has_select_for_update_skip_locked(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def has_select_for_update_nowait(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def has_select_for_update_of(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def supports_explain_analyze(self) -> bool: ...
    @cached_property
    def supported_explain_formats(self) -> set[str]: ...  # type: ignore[override]
    @cached_property
    def supports_transactions(self) -> bool: ...
    @cached_property
    def ignores_table_name_case(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def supports_default_in_lead_lag(self) -> bool: ...  # type: ignore[override]
    @property
    def supports_json_field(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def can_introspect_json_field(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def supports_index_column_ordering(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def supports_expression_indexes(self) -> bool: ...  # type: ignore[override]
