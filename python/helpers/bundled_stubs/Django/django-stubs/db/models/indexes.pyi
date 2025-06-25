from collections.abc import Sequence
from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.backends.ddl_references import Statement
from django.db.models.base import Model
from django.db.models.expressions import BaseExpression, Combinable, Expression, Func
from django.db.models.query_utils import Q
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType

class Index:
    model: type[Model]
    suffix: str
    max_name_length: int
    fields: Sequence[str]
    fields_orders: Sequence[tuple[str, str]]
    name: str
    db_tablespace: str | None
    opclasses: Sequence[str]
    condition: Q | None
    expressions: Sequence[BaseExpression | Combinable]
    include: Sequence[str]
    def __init__(
        self,
        *expressions: BaseExpression | Combinable | str,
        fields: Sequence[str] = (),
        name: str | None = None,
        db_tablespace: str | None = None,
        opclasses: Sequence[str] = (),
        condition: Q | None = None,
        include: Sequence[str] | None = None,
    ) -> None: ...
    @property
    def contains_expressions(self) -> bool: ...
    def create_sql(
        self, model: type[Model], schema_editor: BaseDatabaseSchemaEditor, using: str = "", **kwargs: Any
    ) -> Statement: ...
    def remove_sql(self, model: type[Model], schema_editor: BaseDatabaseSchemaEditor, **kwargs: Any) -> str: ...
    def deconstruct(self) -> tuple[str, Sequence[Any], dict[str, Any]]: ...
    def clone(self) -> Index: ...
    def set_name_with_model(self, model: type[Model]) -> None: ...

class IndexExpression(Func):
    template: str
    wrapper_classes: Sequence[Expression]
    def set_wrapper_classes(self, connection: Any | None = None) -> None: ...
    @classmethod
    def register_wrappers(cls, *wrapper_classes: Expression) -> None: ...
    def resolve_expression(
        self,
        query: Any | None = None,
        allow_joins: bool = True,
        reuse: set[str] | None = None,
        summarize: bool = False,
        for_save: bool = False,
    ) -> IndexExpression: ...
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...
