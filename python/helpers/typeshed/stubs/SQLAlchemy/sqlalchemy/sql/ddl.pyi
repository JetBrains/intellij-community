from typing import Any

from . import roles
from .base import Executable, SchemaVisitor
from .elements import ClauseElement

class _DDLCompiles(ClauseElement): ...

class DDLElement(roles.DDLRole, Executable, _DDLCompiles):
    target: Any
    on: Any
    dialect: Any
    callable_: Any
    def execute(self, bind: Any | None = ..., target: Any | None = ...): ...  # type: ignore[override]
    def against(self, target) -> None: ...
    state: Any
    def execute_if(self, dialect: Any | None = ..., callable_: Any | None = ..., state: Any | None = ...) -> None: ...
    def __call__(self, target, bind, **kw): ...
    bind: Any

class DDL(DDLElement):
    __visit_name__: str
    statement: Any
    context: Any
    def __init__(self, statement, context: Any | None = ..., bind: Any | None = ...) -> None: ...

class _CreateDropBase(DDLElement):
    element: Any
    bind: Any
    if_exists: Any
    if_not_exists: Any
    def __init__(
        self, element, bind: Any | None = ..., if_exists: bool = ..., if_not_exists: bool = ..., _legacy_bind: Any | None = ...
    ) -> None: ...
    @property
    def stringify_dialect(self): ...

class CreateSchema(_CreateDropBase):
    __visit_name__: str
    quote: Any
    def __init__(self, name, quote: Any | None = ..., **kw) -> None: ...

class DropSchema(_CreateDropBase):
    __visit_name__: str
    quote: Any
    cascade: Any
    def __init__(self, name, quote: Any | None = ..., cascade: bool = ..., **kw) -> None: ...

class CreateTable(_CreateDropBase):
    __visit_name__: str
    columns: Any
    include_foreign_key_constraints: Any
    def __init__(
        self, element, bind: Any | None = ..., include_foreign_key_constraints: Any | None = ..., if_not_exists: bool = ...
    ) -> None: ...

class _DropView(_CreateDropBase):
    __visit_name__: str

class CreateColumn(_DDLCompiles):
    __visit_name__: str
    element: Any
    def __init__(self, element) -> None: ...

class DropTable(_CreateDropBase):
    __visit_name__: str
    def __init__(self, element, bind: Any | None = ..., if_exists: bool = ...) -> None: ...

class CreateSequence(_CreateDropBase):
    __visit_name__: str

class DropSequence(_CreateDropBase):
    __visit_name__: str

class CreateIndex(_CreateDropBase):
    __visit_name__: str
    def __init__(self, element, bind: Any | None = ..., if_not_exists: bool = ...) -> None: ...

class DropIndex(_CreateDropBase):
    __visit_name__: str
    def __init__(self, element, bind: Any | None = ..., if_exists: bool = ...) -> None: ...

class AddConstraint(_CreateDropBase):
    __visit_name__: str
    def __init__(self, element, *args, **kw) -> None: ...

class DropConstraint(_CreateDropBase):
    __visit_name__: str
    cascade: Any
    def __init__(self, element, cascade: bool = ..., **kw) -> None: ...

class SetTableComment(_CreateDropBase):
    __visit_name__: str

class DropTableComment(_CreateDropBase):
    __visit_name__: str

class SetColumnComment(_CreateDropBase):
    __visit_name__: str

class DropColumnComment(_CreateDropBase):
    __visit_name__: str

class DDLBase(SchemaVisitor):
    connection: Any
    def __init__(self, connection) -> None: ...

class SchemaGenerator(DDLBase):
    checkfirst: Any
    tables: Any
    preparer: Any
    dialect: Any
    memo: Any
    def __init__(self, dialect, connection, checkfirst: bool = ..., tables: Any | None = ..., **kwargs) -> None: ...
    def visit_metadata(self, metadata) -> None: ...
    def visit_table(
        self, table, create_ok: bool = ..., include_foreign_key_constraints: Any | None = ..., _is_metadata_operation: bool = ...
    ) -> None: ...
    def visit_foreign_key_constraint(self, constraint) -> None: ...
    def visit_sequence(self, sequence, create_ok: bool = ...) -> None: ...
    def visit_index(self, index, create_ok: bool = ...) -> None: ...

class SchemaDropper(DDLBase):
    checkfirst: Any
    tables: Any
    preparer: Any
    dialect: Any
    memo: Any
    def __init__(self, dialect, connection, checkfirst: bool = ..., tables: Any | None = ..., **kwargs) -> None: ...
    def visit_metadata(self, metadata): ...
    def visit_index(self, index, drop_ok: bool = ...) -> None: ...
    def visit_table(self, table, drop_ok: bool = ..., _is_metadata_operation: bool = ..., _ignore_sequences=...) -> None: ...
    def visit_foreign_key_constraint(self, constraint) -> None: ...
    def visit_sequence(self, sequence, drop_ok: bool = ...) -> None: ...

def sort_tables(tables, skip_fn: Any | None = ..., extra_dependencies: Any | None = ...): ...
def sort_tables_and_constraints(
    tables, filter_fn: Any | None = ..., extra_dependencies: Any | None = ..., _warn_for_cycles: bool = ...
): ...
