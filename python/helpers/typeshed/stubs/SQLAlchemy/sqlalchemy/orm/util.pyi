from typing import Any

from ..sql import base as sql_base, expression, util as sql_util
from ..sql.annotation import SupportsCloneAnnotations
from .base import (
    InspectionAttr as InspectionAttr,
    _class_to_mapper as _class_to_mapper,
    _never_set as _never_set,
    _none_set as _none_set,
    attribute_str as attribute_str,
    class_mapper as class_mapper,
    instance_str as instance_str,
    object_mapper as object_mapper,
    object_state as object_state,
    state_attribute_str as state_attribute_str,
    state_class_str as state_class_str,
    state_str as state_str,
)
from .interfaces import CriteriaOption, ORMColumnsClauseRole, ORMEntityColumnsClauseRole, ORMFromClauseRole

all_cascades: Any

class CascadeOptions(frozenset[Any]):
    save_update: Any
    delete: Any
    refresh_expire: Any
    merge: Any
    expunge: Any
    delete_orphan: Any
    def __new__(cls, value_list): ...
    @classmethod
    def from_string(cls, arg): ...

def polymorphic_union(table_map, typecolname, aliasname: str = ..., cast_nulls: bool = ...): ...
def identity_key(*args, **kwargs): ...

class ORMAdapter(sql_util.ColumnAdapter):
    mapper: Any
    aliased_class: Any
    def __init__(
        self,
        entity,
        equivalents: Any | None = ...,
        adapt_required: bool = ...,
        allow_label_resolve: bool = ...,
        anonymize_labels: bool = ...,
    ) -> None: ...

class AliasedClass:
    __name__: Any
    def __init__(
        self,
        mapped_class_or_ac,
        alias: Any | None = ...,
        name: Any | None = ...,
        flat: bool = ...,
        adapt_on_names: bool = ...,
        with_polymorphic_mappers=...,
        with_polymorphic_discriminator: Any | None = ...,
        base_alias: Any | None = ...,
        use_mapper_path: bool = ...,
        represents_outer_join: bool = ...,
    ) -> None: ...
    def __getattr__(self, key): ...

class AliasedInsp(ORMEntityColumnsClauseRole, ORMFromClauseRole, sql_base.MemoizedHasCacheKey, InspectionAttr):
    mapper: Any
    selectable: Any
    name: Any
    polymorphic_on: Any
    represents_outer_join: Any
    with_polymorphic_mappers: Any
    def __init__(
        self,
        entity,
        inspected,
        selectable,
        name,
        with_polymorphic_mappers,
        polymorphic_on,
        _base_alias,
        _use_mapper_path,
        adapt_on_names,
        represents_outer_join,
        nest_adapters: bool,  # added in 1.4.30
    ) -> None: ...
    @property
    def entity(self): ...
    is_aliased_class: bool
    def __clause_element__(self): ...
    @property
    def entity_namespace(self): ...
    @property
    def class_(self): ...

class _WrapUserEntity:
    subject: Any
    def __init__(self, subject) -> None: ...
    def __getattribute__(self, name): ...

class LoaderCriteriaOption(CriteriaOption):
    root_entity: Any
    entity: Any
    deferred_where_criteria: bool
    where_criteria: Any
    include_aliases: Any
    propagate_to_loaders: Any
    def __init__(
        self,
        entity_or_base,
        where_criteria,
        loader_only: bool = ...,
        include_aliases: bool = ...,
        propagate_to_loaders: bool = ...,
        track_closure_variables: bool = ...,
    ) -> None: ...
    def process_compile_state_replaced_entities(self, compile_state, mapper_entities): ...
    def process_compile_state(self, compile_state) -> None: ...
    def get_global_criteria(self, attributes) -> None: ...

def aliased(element, alias: Any | None = ..., name: Any | None = ..., flat: bool = ..., adapt_on_names: bool = ...): ...
def with_polymorphic(
    base,
    classes,
    selectable: bool = ...,
    flat: bool = ...,
    polymorphic_on: Any | None = ...,
    aliased: bool = ...,
    adapt_on_names: bool = ...,
    innerjoin: bool = ...,
    _use_mapper_path: bool = ...,
    _existing_alias: Any | None = ...,
) -> AliasedClass: ...

class Bundle(ORMColumnsClauseRole, SupportsCloneAnnotations, sql_base.MemoizedHasCacheKey, InspectionAttr):
    single_entity: bool
    is_clause_element: bool
    is_mapper: bool
    is_aliased_class: bool
    is_bundle: bool
    name: Any
    exprs: Any
    c: Any
    def __init__(self, name, *exprs, **kw) -> None: ...
    @property
    def mapper(self): ...
    @property
    def entity(self): ...
    @property
    def entity_namespace(self): ...
    columns: Any
    def __clause_element__(self): ...
    @property
    def clauses(self): ...
    def label(self, name): ...
    def create_row_processor(self, query, procs, labels): ...

class _ORMJoin(expression.Join):
    __visit_name__: Any
    inherit_cache: bool
    onclause: Any
    def __init__(
        self,
        left,
        right,
        onclause: Any | None = ...,
        isouter: bool = ...,
        full: bool = ...,
        _left_memo: Any | None = ...,
        _right_memo: Any | None = ...,
        _extra_criteria=...,
    ) -> None: ...
    def join(self, right, onclause: Any | None = ..., isouter: bool = ..., full: bool = ..., join_to_left: Any | None = ...): ...
    def outerjoin(self, right, onclause: Any | None = ..., full: bool = ..., join_to_left: Any | None = ...): ...

def join(left, right, onclause: Any | None = ..., isouter: bool = ..., full: bool = ..., join_to_left: Any | None = ...): ...
def outerjoin(left, right, onclause: Any | None = ..., full: bool = ..., join_to_left: Any | None = ...): ...
def with_parent(instance, prop, from_entity: Any | None = ...): ...
def has_identity(object_): ...
def was_deleted(object_): ...
def randomize_unitofwork() -> None: ...
