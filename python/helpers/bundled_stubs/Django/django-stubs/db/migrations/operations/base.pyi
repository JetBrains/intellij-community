from collections.abc import Sequence
from enum import Enum
from typing import Any, ClassVar

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.migrations.state import ProjectState
from django.db.models import Model
from typing_extensions import Self

class OperationCategory(str, Enum):
    ADDITION = "+"
    REMOVAL = "-"
    ALTERATION = "~"
    PYTHON = "p"
    SQL = "s"
    MIXED = "?"

class Operation:
    reversible: ClassVar[bool]
    reduces_to_sql: ClassVar[bool]
    atomic: ClassVar[bool]
    elidable: ClassVar[bool]
    serialization_expand_args: ClassVar[list[str]]
    category: ClassVar[OperationCategory | None]
    def __new__(cls, *args: Any, **kwargs: Any) -> Self: ...
    def deconstruct(self) -> tuple[str, Sequence[Any], dict[str, Any]]: ...
    def state_forwards(self, app_label: str, state: ProjectState) -> None: ...
    def database_forwards(
        self, app_label: str, schema_editor: BaseDatabaseSchemaEditor, from_state: ProjectState, to_state: ProjectState
    ) -> None: ...
    def database_backwards(
        self, app_label: str, schema_editor: BaseDatabaseSchemaEditor, from_state: ProjectState, to_state: ProjectState
    ) -> None: ...
    def describe(self) -> str: ...
    def formatted_description(self) -> str: ...
    @property
    def migration_name_fragment(self) -> str: ...
    def references_model(self, name: str, app_label: str) -> bool: ...
    def references_field(self, model_name: str, name: str, app_label: str) -> bool: ...
    def allow_migrate_model(self, connection_alias: BaseDatabaseWrapper | str, model: type[Model]) -> bool: ...
    def reduce(self, operation: Operation, app_label: str) -> bool | list[Operation]: ...
