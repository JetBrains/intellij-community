from typing import Any

from ..sql import base as sql_base
from ..util import HasMemoized, memoized_property
from .base import (
    _class_to_mapper as _class_to_mapper,
    _state_mapper as _state_mapper,
    class_mapper as class_mapper,
    state_str as state_str,
)
from .interfaces import InspectionAttr, ORMEntityColumnsClauseRole, ORMFromClauseRole

NO_ATTRIBUTE: Any

class Mapper(ORMFromClauseRole, ORMEntityColumnsClauseRole, sql_base.MemoizedHasCacheKey, InspectionAttr):
    logger: Any
    class_: Any
    class_manager: Any
    non_primary: Any
    always_refresh: Any
    version_id_prop: Any
    version_id_col: Any
    version_id_generator: bool
    concrete: Any
    single: bool
    inherits: Any
    local_table: Any
    inherit_condition: Any
    inherit_foreign_keys: Any
    batch: Any
    eager_defaults: Any
    column_prefix: Any
    polymorphic_on: Any
    validators: Any
    passive_updates: Any
    passive_deletes: Any
    legacy_is_orphan: Any
    allow_partial_pks: Any
    confirm_deleted_rows: bool
    polymorphic_load: Any
    polymorphic_identity: Any
    polymorphic_map: Any
    include_properties: Any
    exclude_properties: Any
    def __init__(
        self,
        class_,
        local_table: Any | None = ...,
        properties: Any | None = ...,
        primary_key: Any | None = ...,
        non_primary: bool = ...,
        inherits: Any | None = ...,
        inherit_condition: Any | None = ...,
        inherit_foreign_keys: Any | None = ...,
        always_refresh: bool = ...,
        version_id_col: Any | None = ...,
        version_id_generator: Any | None = ...,
        polymorphic_on: Any | None = ...,
        _polymorphic_map: Any | None = ...,
        polymorphic_identity: Any | None = ...,
        concrete: bool = ...,
        with_polymorphic: Any | None = ...,
        polymorphic_load: Any | None = ...,
        allow_partial_pks: bool = ...,
        batch: bool = ...,
        column_prefix: Any | None = ...,
        include_properties: Any | None = ...,
        exclude_properties: Any | None = ...,
        passive_updates: bool = ...,
        passive_deletes: bool = ...,
        confirm_deleted_rows: bool = ...,
        eager_defaults: bool = ...,
        legacy_is_orphan: bool = ...,
        _compiled_cache_size: int = ...,
    ): ...
    is_mapper: bool
    represents_outer_join: bool
    @property
    def mapper(self): ...
    @property
    def entity(self): ...
    persist_selectable: Any
    configured: bool
    tables: Any
    primary_key: Any
    base_mapper: Any
    columns: Any
    c: Any
    @property
    def mapped_table(self): ...
    def add_properties(self, dict_of_properties) -> None: ...
    def add_property(self, key, prop) -> None: ...
    def has_property(self, key): ...
    def get_property(self, key, _configure_mappers: bool = ...): ...
    def get_property_by_column(self, column): ...
    @property
    def iterate_properties(self): ...
    with_polymorphic_mappers: Any
    def __clause_element__(self): ...
    @memoized_property
    def select_identity_token(self): ...
    @property
    def selectable(self): ...
    @HasMemoized.memoized_attribute
    def attrs(self): ...
    @HasMemoized.memoized_attribute
    def all_orm_descriptors(self): ...
    @HasMemoized.memoized_attribute
    def synonyms(self): ...
    @property
    def entity_namespace(self): ...
    @HasMemoized.memoized_attribute
    def column_attrs(self): ...
    @HasMemoized.memoized_attribute
    def relationships(self): ...
    @HasMemoized.memoized_attribute
    def composites(self): ...
    def common_parent(self, other): ...
    def is_sibling(self, other): ...
    def isa(self, other): ...
    def iterate_to_root(self) -> None: ...
    @HasMemoized.memoized_attribute
    def self_and_descendants(self): ...
    def polymorphic_iterator(self): ...
    def primary_mapper(self): ...
    @property
    def primary_base_mapper(self): ...
    def identity_key_from_row(self, row, identity_token: Any | None = ..., adapter: Any | None = ...): ...
    def identity_key_from_primary_key(self, primary_key, identity_token: Any | None = ...): ...
    def identity_key_from_instance(self, instance): ...
    def primary_key_from_instance(self, instance): ...
    def cascade_iterator(self, type_, state, halt_on: Any | None = ...) -> None: ...

class _OptGetColumnsNotAvailable(Exception): ...

def configure_mappers() -> None: ...
def reconstructor(fn): ...
def validates(*names, **kw): ...

class _ColumnMapping(dict[Any, Any]):
    mapper: Any
    def __init__(self, mapper) -> None: ...
    def __missing__(self, column) -> None: ...
