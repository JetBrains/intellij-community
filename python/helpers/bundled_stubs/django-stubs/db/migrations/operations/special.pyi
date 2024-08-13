from collections.abc import Mapping, Sequence
from typing import Any, Literal, Protocol, type_check_only

from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.migrations.state import StateApps
from django.utils.datastructures import _ListOrTuple
from typing_extensions import TypeAlias

from .base import Operation

_SqlOperations: TypeAlias = str | _ListOrTuple[str | tuple[str, dict[str, Any] | _ListOrTuple[Any] | None]]

class SeparateDatabaseAndState(Operation):
    database_operations: Sequence[Operation]
    state_operations: Sequence[Operation]

    def __init__(
        self, database_operations: Sequence[Operation] | None = ..., state_operations: Sequence[Operation] | None = ...
    ) -> None: ...

class RunSQL(Operation):
    noop: Literal[""]
    sql: _SqlOperations
    reverse_sql: _SqlOperations | None
    state_operations: Sequence[Operation]
    hints: Mapping[str, Any]
    def __init__(
        self,
        sql: _SqlOperations,
        reverse_sql: _SqlOperations | None = ...,
        state_operations: Sequence[Operation] | None = ...,
        hints: Mapping[str, Any] | None = ...,
        elidable: bool = ...,
    ) -> None: ...
    @property
    def reversible(self) -> bool: ...  # type: ignore[override]

@type_check_only
class _CodeCallable(Protocol):
    def __call__(self, state_apps: StateApps, schema_editor: BaseDatabaseSchemaEditor, /) -> None: ...

class RunPython(Operation):
    code: _CodeCallable
    reverse_code: _CodeCallable | None
    hints: Mapping[str, Any]
    def __init__(
        self,
        code: _CodeCallable,
        reverse_code: _CodeCallable | None = ...,
        atomic: bool | None = ...,
        hints: Mapping[str, Any] | None = ...,
        elidable: bool = ...,
    ) -> None: ...
    @staticmethod
    def noop(apps: StateApps, schema_editor: BaseDatabaseSchemaEditor) -> None: ...
    @property
    def reversible(self) -> bool: ...  # type: ignore[override]
