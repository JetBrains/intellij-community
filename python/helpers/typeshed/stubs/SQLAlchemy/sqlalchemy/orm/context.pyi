from _typeshed import Incomplete
from typing import Any

from ..sql.base import CacheableOptions, CompileState, Options
from ..sql.selectable import SelectState

LABEL_STYLE_LEGACY_ORM: Any

class QueryContext:
    class default_load_options(Options): ...
    load_options: Any
    execution_options: Any
    bind_arguments: Any
    compile_state: Any
    query: Any
    session: Any
    loaders_require_buffering: bool
    loaders_require_uniquing: bool
    params: Any
    create_eager_joins: Any
    propagated_loader_options: Any
    attributes: Any
    runid: Any
    partials: Any
    post_load_paths: Any
    autoflush: Any
    populate_existing: Any
    invoke_all_eagers: Any
    version_check: Any
    refresh_state: Any
    yield_per: Any
    identity_token: Any
    def __init__(
        self,
        compile_state,
        statement,
        params,
        session,
        load_options,
        execution_options: Any | None = ...,
        bind_arguments: Any | None = ...,
    ) -> None: ...

class ORMCompileState(CompileState):
    class default_compile_options(CacheableOptions): ...
    current_path: Any
    def __init__(self, *arg, **kw) -> None: ...
    @classmethod
    def create_for_statement(cls, statement_container, compiler, **kw) -> None: ...  # type: ignore[override]
    @classmethod
    def get_column_descriptions(cls, statement): ...
    @classmethod
    def orm_pre_session_exec(cls, session, statement, params, execution_options, bind_arguments, is_reentrant_invoke): ...
    @classmethod
    def orm_setup_cursor_result(cls, session, statement, params, execution_options, bind_arguments, result): ...

class ORMFromStatementCompileState(ORMCompileState):
    multi_row_eager_loaders: bool
    compound_eager_adapter: Any
    extra_criteria_entities: Any
    eager_joins: Any
    use_legacy_query_style: Any
    statement_container: Any
    requested_statement: Any
    dml_table: Any
    compile_options: Any
    statement: Any
    current_path: Any
    attributes: Any
    global_attributes: Any
    primary_columns: Any
    secondary_columns: Any
    dedupe_columns: Any
    create_eager_joins: Any
    order_by: Any
    @classmethod
    def create_for_statement(cls, statement_container, compiler, **kw): ...

class ORMSelectCompileState(ORMCompileState, SelectState):
    multi_row_eager_loaders: bool
    compound_eager_adapter: Any
    correlate: Any
    correlate_except: Any
    global_attributes: Any
    select_statement: Any
    for_statement: Any
    use_legacy_query_style: Any
    compile_options: Any
    label_style: Any
    current_path: Any
    eager_order_by: Any
    attributes: Any
    primary_columns: Any
    secondary_columns: Any
    dedupe_columns: Any
    eager_joins: Any
    extra_criteria_entities: Any
    create_eager_joins: Any
    from_clauses: Any
    @classmethod
    def create_for_statement(cls, statement, compiler, **kw): ...
    @classmethod
    def determine_last_joined_entity(cls, statement): ...
    @classmethod
    def all_selected_columns(cls, statement) -> None: ...
    @classmethod
    def get_columns_clause_froms(cls, statement): ...
    @classmethod
    def from_statement(cls, statement, from_statement): ...

class _QueryEntity:
    use_id_for_hash: bool
    @classmethod
    def to_compile_state(cls, compile_state, entities, entities_collection, is_current_entities): ...

class _MapperEntity(_QueryEntity):
    expr: Any
    mapper: Any
    entity_zero: Any
    is_aliased_class: Any
    path: Any
    selectable: Any
    def __init__(self, compile_state, entity, entities_collection, is_current_entities) -> None: ...
    supports_single_entity: bool
    use_id_for_hash: bool
    @property
    def type(self): ...
    @property
    def entity_zero_or_selectable(self): ...
    def corresponds_to(self, entity): ...
    def row_processor(self, context, result): ...
    def setup_compile_state(self, compile_state) -> None: ...

class _BundleEntity(_QueryEntity):
    bundle: Any
    expr: Any
    type: Any
    supports_single_entity: Any
    def __init__(
        self,
        compile_state,
        expr,
        entities_collection,
        is_current_entities: bool,
        setup_entities: bool = ...,
        parent_bundle: Incomplete | None = ...,
    ) -> None: ...
    @property
    def mapper(self): ...
    @property
    def entity_zero(self): ...
    def corresponds_to(self, entity): ...
    @property
    def entity_zero_or_selectable(self): ...
    def setup_compile_state(self, compile_state) -> None: ...
    def row_processor(self, context, result): ...

class _ColumnEntity(_QueryEntity):
    raw_column_index: Any
    translate_raw_column: Any
    @property
    def type(self): ...
    def row_processor(self, context, result): ...

class _RawColumnEntity(_ColumnEntity):
    entity_zero: Any
    mapper: Any
    supports_single_entity: bool
    expr: Any
    raw_column_index: Any
    translate_raw_column: Any
    column: Any
    entity_zero_or_selectable: Any
    def __init__(
        self,
        compile_state,
        column,
        entities_collection,
        raw_column_index,
        is_current_entities: bool,
        parent_bundle: Incomplete | None = ...,
    ) -> None: ...
    def corresponds_to(self, entity): ...
    def setup_compile_state(self, compile_state) -> None: ...

class _ORMColumnEntity(_ColumnEntity):
    supports_single_entity: bool
    expr: Any
    translate_raw_column: bool
    raw_column_index: Any
    entity_zero_or_selectable: Any
    entity_zero: Any
    mapper: Any
    column: Any
    def __init__(
        self,
        compile_state,
        column,
        entities_collection,
        parententity,
        raw_column_index,
        is_current_entities: bool,
        parent_bundle: Incomplete | None = ...,
    ) -> None: ...
    def corresponds_to(self, entity): ...
    def setup_compile_state(self, compile_state) -> None: ...

class _IdentityTokenEntity(_ORMColumnEntity):
    translate_raw_column: bool
    def setup_compile_state(self, compile_state) -> None: ...
    def row_processor(self, context, result): ...
