from typing import Any

from django.db.backends.base.features import BaseDatabaseFeatures
from django.db.backends.postgresql.base import DatabaseWrapper
from django.utils.functional import cached_property

class DatabaseFeatures(BaseDatabaseFeatures):
    connection: DatabaseWrapper
    allows_group_by_selected_pks: bool
    can_return_columns_from_insert: bool
    can_return_rows_from_bulk_insert: bool
    has_real_datatype: bool
    has_native_uuid_field: bool
    has_native_duration_field: bool
    has_native_json_field: bool
    can_defer_constraint_checks: bool
    has_select_for_update: bool
    has_select_for_update_nowait: bool
    has_select_for_update_of: bool
    has_select_for_update_skip_locked: bool
    has_select_for_no_key_update: bool
    can_release_savepoints: bool
    supports_tablespaces: bool
    supports_transactions: bool
    can_introspect_materialized_views: bool
    can_distinct_on_fields: bool
    can_rollback_ddl: bool
    supports_combined_alters: bool
    nulls_order_largest: bool
    closed_cursor_error_class: Any
    has_case_insensitive_like: bool
    greatest_least_ignores_nulls: bool
    can_clone_databases: bool
    supports_temporal_subtraction: bool
    supports_slicing_ordering_in_compound: bool
    create_test_procedure_without_params_sql: str
    create_test_procedure_with_int_param_sql: str
    requires_casted_case_in_updates: bool
    supports_over_clause: bool
    only_supports_unbounded_with_preceding_and_following: bool
    supports_aggregate_filter_clause: bool
    supported_explain_formats: Any
    validates_explain_options: bool
    supports_deferrable_unique_constraints: bool
    has_json_operators: bool
    json_key_contains_list_matching_requires_list: bool
    @property
    def is_postgresql_10(self) -> bool: ...
    @property
    def is_postgresql_11(self) -> bool: ...
    @property
    def is_postgresql_12(self) -> bool: ...
    @cached_property
    def is_postgresql_13(self) -> bool: ...
    has_brin_autosummarize: bool
    has_websearch_to_tsquery: bool
    supports_table_partitions: bool
    supports_covering_indexes: bool
    supports_covering_gist_indexes: bool
    supports_non_deterministic_collations: bool
    supports_alternate_collation_providers: bool
