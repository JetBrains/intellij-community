from typing import Any

from ..util import memoized_property
from . import visitors
from .base import DialectKWArgs, Executable, SchemaEventTarget
from .elements import ColumnClause
from .selectable import TableClause

RETAIN_SCHEMA: Any
BLANK_SCHEMA: Any
NULL_UNSPECIFIED: Any

class SchemaItem(SchemaEventTarget, visitors.Visitable):
    __visit_name__: str
    create_drop_stringify_dialect: str
    @memoized_property
    def info(self): ...

class Table(DialectKWArgs, SchemaItem, TableClause):
    __visit_name__: str
    constraints: Any
    indexes: Any
    def __new__(cls, *args, **kw): ...
    def __init__(self, *args, **kw) -> None: ...
    @property
    def foreign_key_constraints(self): ...
    @property
    def key(self): ...
    @property
    def bind(self): ...
    def add_is_dependent_on(self, table) -> None: ...
    def append_column(self, column, replace_existing: bool = ...) -> None: ...  # type: ignore[override]
    def append_constraint(self, constraint) -> None: ...
    def exists(self, bind: Any | None = ...): ...
    def create(self, bind: Any | None = ..., checkfirst: bool = ...) -> None: ...
    def drop(self, bind: Any | None = ..., checkfirst: bool = ...) -> None: ...
    def tometadata(self, metadata, schema=..., referred_schema_fn: Any | None = ..., name: Any | None = ...): ...
    def to_metadata(self, metadata, schema=..., referred_schema_fn: Any | None = ..., name: Any | None = ...): ...

class Column(DialectKWArgs, SchemaItem, ColumnClause):
    __visit_name__: str
    inherit_cache: bool
    key: Any
    primary_key: Any
    nullable: Any
    default: Any
    server_default: Any
    server_onupdate: Any
    index: Any
    unique: Any
    system: Any
    doc: Any
    onupdate: Any
    autoincrement: Any
    constraints: Any
    foreign_keys: Any
    comment: Any
    computed: Any
    identity: Any
    info: Any
    def __init__(self, *args, **kwargs) -> None: ...
    def references(self, column): ...
    def append_foreign_key(self, fk) -> None: ...
    def copy(self, **kw): ...

class ForeignKey(DialectKWArgs, SchemaItem):
    __visit_name__: str
    constraint: Any
    parent: Any
    use_alter: Any
    name: Any
    onupdate: Any
    ondelete: Any
    deferrable: Any
    initially: Any
    link_to_name: Any
    match: Any
    info: Any
    def __init__(
        self,
        column,
        _constraint: Any | None = ...,
        use_alter: bool = ...,
        name: Any | None = ...,
        onupdate: Any | None = ...,
        ondelete: Any | None = ...,
        deferrable: Any | None = ...,
        initially: Any | None = ...,
        link_to_name: bool = ...,
        match: Any | None = ...,
        info: Any | None = ...,
        **dialect_kw,
    ) -> None: ...
    def copy(self, schema: Any | None = ..., **kw): ...
    target_fullname: Any
    def references(self, table): ...
    def get_referent(self, table): ...
    @memoized_property
    def column(self): ...

class DefaultGenerator(Executable, SchemaItem):
    __visit_name__: str
    is_sequence: bool
    is_server_default: bool
    column: Any
    for_update: Any
    def __init__(self, for_update: bool = ...) -> None: ...
    def execute(self, bind: Any | None = ...): ...  # type: ignore[override]
    @property
    def bind(self): ...

class ColumnDefault(DefaultGenerator):
    arg: Any
    def __init__(self, arg, **kwargs) -> None: ...
    @memoized_property
    def is_callable(self): ...
    @memoized_property
    def is_clause_element(self): ...
    @memoized_property
    def is_scalar(self): ...

class IdentityOptions:
    start: Any
    increment: Any
    minvalue: Any
    maxvalue: Any
    nominvalue: Any
    nomaxvalue: Any
    cycle: Any
    cache: Any
    order: Any
    def __init__(
        self,
        start: Any | None = ...,
        increment: Any | None = ...,
        minvalue: Any | None = ...,
        maxvalue: Any | None = ...,
        nominvalue: Any | None = ...,
        nomaxvalue: Any | None = ...,
        cycle: Any | None = ...,
        cache: Any | None = ...,
        order: Any | None = ...,
    ) -> None: ...

class Sequence(IdentityOptions, DefaultGenerator):
    __visit_name__: str
    is_sequence: bool
    name: Any
    optional: Any
    schema: Any
    metadata: Any
    data_type: Any
    def __init__(
        self,
        name,
        start: Any | None = ...,
        increment: Any | None = ...,
        minvalue: Any | None = ...,
        maxvalue: Any | None = ...,
        nominvalue: Any | None = ...,
        nomaxvalue: Any | None = ...,
        cycle: Any | None = ...,
        schema: Any | None = ...,
        cache: Any | None = ...,
        order: Any | None = ...,
        data_type: Any | None = ...,
        optional: bool = ...,
        quote: Any | None = ...,
        metadata: Any | None = ...,
        quote_schema: Any | None = ...,
        for_update: bool = ...,
    ) -> None: ...
    @memoized_property
    def is_callable(self): ...
    @memoized_property
    def is_clause_element(self): ...
    def next_value(self): ...
    @property
    def bind(self): ...
    def create(self, bind: Any | None = ..., checkfirst: bool = ...) -> None: ...
    def drop(self, bind: Any | None = ..., checkfirst: bool = ...) -> None: ...

class FetchedValue(SchemaEventTarget):
    is_server_default: bool
    reflected: bool
    has_argument: bool
    is_clause_element: bool
    for_update: Any
    def __init__(self, for_update: bool = ...) -> None: ...

class DefaultClause(FetchedValue):
    has_argument: bool
    arg: Any
    reflected: Any
    def __init__(self, arg, for_update: bool = ..., _reflected: bool = ...) -> None: ...

class Constraint(DialectKWArgs, SchemaItem):
    __visit_name__: str
    name: Any
    deferrable: Any
    initially: Any
    info: Any
    def __init__(
        self,
        name: Any | None = ...,
        deferrable: Any | None = ...,
        initially: Any | None = ...,
        _create_rule: Any | None = ...,
        info: Any | None = ...,
        _type_bound: bool = ...,
        **dialect_kw,
    ) -> None: ...
    @property
    def table(self): ...
    def copy(self, **kw): ...

class ColumnCollectionMixin:
    columns: Any
    def __init__(self, *columns, **kw) -> None: ...

class ColumnCollectionConstraint(ColumnCollectionMixin, Constraint):
    def __init__(self, *columns, **kw) -> None: ...
    columns: Any
    def __contains__(self, x): ...
    def copy(self, target_table: Any | None = ..., **kw): ...
    def contains_column(self, col): ...
    def __iter__(self): ...
    def __len__(self): ...

class CheckConstraint(ColumnCollectionConstraint):
    __visit_name__: str
    sqltext: Any
    def __init__(
        self,
        sqltext,
        name: Any | None = ...,
        deferrable: Any | None = ...,
        initially: Any | None = ...,
        table: Any | None = ...,
        info: Any | None = ...,
        _create_rule: Any | None = ...,
        _autoattach: bool = ...,
        _type_bound: bool = ...,
        **kw,
    ) -> None: ...
    @property
    def is_column_level(self): ...
    def copy(self, target_table: Any | None = ..., **kw): ...

class ForeignKeyConstraint(ColumnCollectionConstraint):
    __visit_name__: str
    onupdate: Any
    ondelete: Any
    link_to_name: Any
    use_alter: Any
    match: Any
    elements: Any
    def __init__(
        self,
        columns,
        refcolumns,
        name: Any | None = ...,
        onupdate: Any | None = ...,
        ondelete: Any | None = ...,
        deferrable: Any | None = ...,
        initially: Any | None = ...,
        use_alter: bool = ...,
        link_to_name: bool = ...,
        match: Any | None = ...,
        table: Any | None = ...,
        info: Any | None = ...,
        **dialect_kw,
    ) -> None: ...
    columns: Any
    @property
    def referred_table(self): ...
    @property
    def column_keys(self): ...
    def copy(self, schema: Any | None = ..., target_table: Any | None = ..., **kw): ...  # type: ignore[override]

class PrimaryKeyConstraint(ColumnCollectionConstraint):
    __visit_name__: str
    def __init__(self, *columns, **kw) -> None: ...
    @property
    def columns_autoinc_first(self): ...

class UniqueConstraint(ColumnCollectionConstraint):
    __visit_name__: str

class Index(DialectKWArgs, ColumnCollectionMixin, SchemaItem):
    __visit_name__: str
    table: Any
    name: Any
    unique: Any
    info: Any
    expressions: Any
    def __init__(self, name, *expressions, **kw) -> None: ...
    @property
    def bind(self): ...
    def create(self, bind: Any | None = ..., checkfirst: bool = ...): ...
    def drop(self, bind: Any | None = ..., checkfirst: bool = ...) -> None: ...

DEFAULT_NAMING_CONVENTION: Any

class MetaData(SchemaItem):
    __visit_name__: str
    tables: Any
    schema: Any
    naming_convention: Any
    info: Any
    def __init__(
        self,
        bind: Any | None = ...,
        schema: Any | None = ...,
        quote_schema: Any | None = ...,
        naming_convention: Any | None = ...,
        info: Any | None = ...,
    ) -> None: ...
    def __contains__(self, table_or_key): ...
    def is_bound(self): ...
    bind: Any
    def clear(self) -> None: ...
    def remove(self, table) -> None: ...
    @property
    def sorted_tables(self): ...
    def reflect(
        self,
        bind: Any | None = ...,
        schema: Any | None = ...,
        views: bool = ...,
        only: Any | None = ...,
        extend_existing: bool = ...,
        autoload_replace: bool = ...,
        resolve_fks: bool = ...,
        **dialect_kwargs,
    ) -> None: ...
    def create_all(self, bind: Any | None = ..., tables: Any | None = ..., checkfirst: bool = ...) -> None: ...
    def drop_all(self, bind: Any | None = ..., tables: Any | None = ..., checkfirst: bool = ...) -> None: ...

class ThreadLocalMetaData(MetaData):
    __visit_name__: str
    context: Any
    def __init__(self) -> None: ...
    bind: Any
    def is_bound(self): ...
    def dispose(self) -> None: ...

class Computed(FetchedValue, SchemaItem):
    __visit_name__: str
    sqltext: Any
    persisted: Any
    column: Any
    def __init__(self, sqltext, persisted: Any | None = ...) -> None: ...
    def copy(self, target_table: Any | None = ..., **kw): ...

class Identity(IdentityOptions, FetchedValue, SchemaItem):
    __visit_name__: str
    always: Any
    on_null: Any
    column: Any
    def __init__(
        self,
        always: bool = ...,
        on_null: Any | None = ...,
        start: Any | None = ...,
        increment: Any | None = ...,
        minvalue: Any | None = ...,
        maxvalue: Any | None = ...,
        nominvalue: Any | None = ...,
        nomaxvalue: Any | None = ...,
        cycle: Any | None = ...,
        cache: Any | None = ...,
        order: Any | None = ...,
    ) -> None: ...
    def copy(self, **kw): ...
