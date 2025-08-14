from collections.abc import Sequence
from typing import Any

from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.backends.ddl_references import Statement
from django.db.models import Func, Index, Model
from django.db.models.expressions import BaseExpression, Combinable
from django.db.models.query_utils import Q
from django.utils.datastructures import _ListOrTuple
from django.utils.functional import cached_property

class PostgresIndex(Index):
    @cached_property
    def max_name_length(self) -> int: ...  # type: ignore[override]
    def create_sql(
        self, model: type[Model], schema_editor: BaseDatabaseSchemaEditor, using: str = "", **kwargs: Any
    ) -> Statement: ...
    def check_supported(self, schema_editor: BaseDatabaseSchemaEditor) -> None: ...
    def get_with_params(self) -> Sequence[str]: ...

class BloomIndex(PostgresIndex):
    def __init__(
        self,
        *expressions: BaseExpression | Combinable | str,
        length: int | None = None,
        columns: _ListOrTuple[int] = (),
        fields: Sequence[str] = ...,
        name: str | None = ...,
        db_tablespace: str | None = ...,
        opclasses: Sequence[str] = ...,
        condition: Q | None = ...,
        include: Sequence[str] | None = ...,
    ) -> None: ...

class BrinIndex(PostgresIndex):
    def __init__(
        self,
        *expressions: BaseExpression | Combinable | str,
        autosummarize: bool | None = None,
        pages_per_range: int | None = None,
        fields: Sequence[str] = ...,
        name: str | None = ...,
        db_tablespace: str | None = ...,
        opclasses: Sequence[str] = ...,
        condition: Q | None = ...,
        include: Sequence[str] | None = ...,
    ) -> None: ...

class BTreeIndex(PostgresIndex):
    def __init__(
        self,
        *expressions: BaseExpression | Combinable | str,
        fillfactor: int | None = None,
        fields: Sequence[str] = ...,
        name: str | None = ...,
        db_tablespace: str | None = ...,
        opclasses: Sequence[str] = ...,
        condition: Q | None = ...,
        include: Sequence[str] | None = ...,
    ) -> None: ...

class GinIndex(PostgresIndex):
    def __init__(
        self,
        *expressions: BaseExpression | Combinable | str,
        fastupdate: bool | None = None,
        gin_pending_list_limit: int | None = None,
        fields: Sequence[str] = ...,
        name: str | None = ...,
        db_tablespace: str | None = ...,
        opclasses: Sequence[str] = ...,
        condition: Q | None = ...,
        include: Sequence[str] | None = ...,
    ) -> None: ...

class GistIndex(PostgresIndex):
    def __init__(
        self,
        *expressions: BaseExpression | Combinable | str,
        buffering: bool | None = None,
        fillfactor: int | None = None,
        fields: Sequence[str] = ...,
        name: str | None = ...,
        db_tablespace: str | None = ...,
        opclasses: Sequence[str] = ...,
        condition: Q | None = ...,
        include: Sequence[str] | None = ...,
    ) -> None: ...

class HashIndex(PostgresIndex):
    def __init__(
        self,
        *expressions: BaseExpression | Combinable | str,
        fillfactor: int | None = None,
        fields: Sequence[str] = ...,
        name: str | None = ...,
        db_tablespace: str | None = ...,
        opclasses: Sequence[str] = ...,
        condition: Q | None = ...,
        include: Sequence[str] | None = ...,
    ) -> None: ...

class SpGistIndex(PostgresIndex):
    def __init__(
        self,
        *expressions: BaseExpression | Combinable | str,
        fillfactor: int | None = None,
        fields: Sequence[str] = ...,
        name: str | None = ...,
        db_tablespace: str | None = ...,
        opclasses: Sequence[str] = ...,
        condition: Q | None = ...,
        include: Sequence[str] | None = ...,
    ) -> None: ...

class OpClass(Func):
    def __init__(
        self,
        expression: BaseExpression | Combinable | str,
        name: str,
    ) -> None: ...
