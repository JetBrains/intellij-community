from typing import Any

from ..util import memoized_property
from .interfaces import PropComparator, StrategizedProperty

def remote(expr): ...
def foreign(expr): ...

class RelationshipProperty(StrategizedProperty):
    logger: Any
    strategy_wildcard_key: str
    inherit_cache: bool
    uselist: Any
    argument: Any
    secondary: Any
    primaryjoin: Any
    secondaryjoin: Any
    post_update: Any
    direction: Any
    viewonly: Any
    sync_backref: Any
    lazy: Any
    single_parent: Any
    collection_class: Any
    passive_deletes: Any
    cascade_backrefs: Any
    passive_updates: Any
    remote_side: Any
    enable_typechecks: Any
    query_class: Any
    innerjoin: Any
    distinct_target_key: Any
    doc: Any
    active_history: Any
    join_depth: Any
    omit_join: Any
    local_remote_pairs: Any
    bake_queries: Any
    load_on_pending: Any
    comparator_factory: Any
    comparator: Any
    info: Any
    strategy_key: Any
    order_by: Any
    back_populates: Any
    backref: Any
    def __init__(
        self,
        argument,
        secondary: Any | None = ...,
        primaryjoin: Any | None = ...,
        secondaryjoin: Any | None = ...,
        foreign_keys: Any | None = ...,
        uselist: Any | None = ...,
        order_by: bool = ...,
        backref: Any | None = ...,
        back_populates: Any | None = ...,
        overlaps: Any | None = ...,
        post_update: bool = ...,
        cascade: bool = ...,
        viewonly: bool = ...,
        lazy: str = ...,
        collection_class: Any | None = ...,
        passive_deletes=...,
        passive_updates=...,
        remote_side: Any | None = ...,
        enable_typechecks=...,
        join_depth: Any | None = ...,
        comparator_factory: Any | None = ...,
        single_parent: bool = ...,
        innerjoin: bool = ...,
        distinct_target_key: Any | None = ...,
        doc: Any | None = ...,
        active_history=...,
        cascade_backrefs=...,
        load_on_pending: bool = ...,
        bake_queries: bool = ...,
        _local_remote_pairs: Any | None = ...,
        query_class: Any | None = ...,
        info: Any | None = ...,
        omit_join: Any | None = ...,
        sync_backref: Any | None = ...,
        _legacy_inactive_history_style: bool = ...,
    ) -> None: ...
    def instrument_class(self, mapper) -> None: ...

    class Comparator(PropComparator):
        prop: Any
        def __init__(
            self, prop, parentmapper, adapt_to_entity: Any | None = ..., of_type: Any | None = ..., extra_criteria=...
        ) -> None: ...
        def adapt_to_entity(self, adapt_to_entity): ...
        @memoized_property
        def entity(self): ...
        @memoized_property
        def mapper(self): ...
        def __clause_element__(self): ...
        def of_type(self, cls): ...
        def and_(self, *other): ...
        def in_(self, other) -> None: ...
        __hash__: Any
        def __eq__(self, other): ...
        def any(self, criterion: Any | None = ..., **kwargs): ...
        def has(self, criterion: Any | None = ..., **kwargs): ...
        def contains(self, other, **kwargs): ...
        def __ne__(self, other): ...
        @memoized_property
        def property(self): ...

    def merge(
        self, session, source_state, source_dict, dest_state, dest_dict, load, _recursive, _resolve_conflict_map
    ) -> None: ...
    def cascade_iterator(self, type_, state, dict_, visited_states, halt_on: Any | None = ...) -> None: ...
    @memoized_property
    def entity(self): ...
    @memoized_property
    def mapper(self): ...
    def do_init(self) -> None: ...
    @property
    def cascade(self): ...
    @cascade.setter
    def cascade(self, cascade) -> None: ...

class JoinCondition:
    parent_persist_selectable: Any
    parent_local_selectable: Any
    child_persist_selectable: Any
    child_local_selectable: Any
    parent_equivalents: Any
    child_equivalents: Any
    primaryjoin: Any
    secondaryjoin: Any
    secondary: Any
    consider_as_foreign_keys: Any
    prop: Any
    self_referential: Any
    support_sync: Any
    can_be_synced_fn: Any
    def __init__(
        self,
        parent_persist_selectable,
        child_persist_selectable,
        parent_local_selectable,
        child_local_selectable,
        primaryjoin: Any | None = ...,
        secondary: Any | None = ...,
        secondaryjoin: Any | None = ...,
        parent_equivalents: Any | None = ...,
        child_equivalents: Any | None = ...,
        consider_as_foreign_keys: Any | None = ...,
        local_remote_pairs: Any | None = ...,
        remote_side: Any | None = ...,
        self_referential: bool = ...,
        prop: Any | None = ...,
        support_sync: bool = ...,
        can_be_synced_fn=...,
    ): ...
    @property
    def primaryjoin_minus_local(self): ...
    @property
    def secondaryjoin_minus_local(self): ...
    @memoized_property
    def primaryjoin_reverse_remote(self): ...
    @memoized_property
    def remote_columns(self): ...
    @memoized_property
    def local_columns(self): ...
    @memoized_property
    def foreign_key_columns(self): ...
    def join_targets(self, source_selectable, dest_selectable, aliased, single_crit: Any | None = ..., extra_criteria=...): ...
    def create_lazy_clause(self, reverse_direction: bool = ...): ...

class _ColInAnnotations:
    name: Any
    def __init__(self, name) -> None: ...
    def __call__(self, c): ...
