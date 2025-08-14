from collections.abc import Sequence
from typing import Any

from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.migrations.operations.base import Operation
from django.db.models.base import Model
from django.db.models.constraints import BaseConstraint
from django.db.models.fields import Field
from django.db.models.indexes import Index
from django.db.models.manager import Manager
from django.db.models.options import _OptionTogetherT
from django.utils.functional import cached_property

class ModelOperation(Operation):
    name: str
    def __init__(self, name: str) -> None: ...
    @cached_property
    def name_lower(self) -> str: ...
    def can_reduce_through(self, operation: Operation, app_label: str) -> bool: ...

class CreateModel(ModelOperation):
    fields: list[tuple[str, Field]]
    options: dict[str, Any]
    bases: Sequence[type[Model] | str] | None
    managers: Sequence[tuple[str, Manager]] | None
    def __init__(
        self,
        name: str,
        fields: list[tuple[str, Field]],
        options: dict[str, Any] | None = None,
        bases: Sequence[type[Any] | str] | None = None,
        managers: Sequence[tuple[str, Manager]] | None = None,
    ) -> None: ...

class DeleteModel(ModelOperation): ...

class RenameModel(ModelOperation):
    old_name: str
    new_name: str
    def __init__(self, old_name: str, new_name: str) -> None: ...
    @cached_property
    def old_name_lower(self) -> str: ...
    @cached_property
    def new_name_lower(self) -> str: ...

class ModelOptionOperation(ModelOperation): ...

class AlterModelTable(ModelOptionOperation):
    table: str | None
    def __init__(self, name: str, table: str | None) -> None: ...

class AlterModelTableComment(ModelOptionOperation):
    table_comment: str
    def __init__(self, name: str, table_comment: str) -> None: ...
    def state_forwards(self, app_label: str, state: Any) -> None: ...
    def database_forwards(
        self, app_label: str, schema_editor: BaseDatabaseSchemaEditor, from_state: Any, to_state: Any
    ) -> None: ...
    def database_backwards(
        self, app_label: str, schema_editor: BaseDatabaseSchemaEditor, from_state: Any, to_state: Any
    ) -> None: ...
    def describe(self) -> str: ...
    @property
    def migration_name_fragment(self) -> str: ...

class AlterTogetherOptionOperation(ModelOptionOperation):
    option_name: str
    def __init__(
        self,
        name: str,
        option_value: _OptionTogetherT | None,
    ) -> None: ...
    @cached_property
    def option_value(self) -> set[tuple[str, ...]] | None: ...
    def state_forwards(self, app_label: str, state: Any) -> None: ...
    def database_forwards(
        self, app_label: str, schema_editor: BaseDatabaseSchemaEditor, from_state: Any, to_state: Any
    ) -> None: ...
    def database_backwards(
        self, app_label: str, schema_editor: BaseDatabaseSchemaEditor, from_state: Any, to_state: Any
    ) -> None: ...
    def references_field(self, model_name: str, name: str, app_label: str) -> bool: ...
    def describe(self) -> str: ...
    @property
    def migration_name_fragment(self) -> str: ...

class AlterUniqueTogether(AlterTogetherOptionOperation):
    option_name: str
    unique_together: set[tuple[str, ...]] | None
    def __init__(self, name: str, unique_together: _OptionTogetherT | None) -> None: ...

class AlterIndexTogether(AlterTogetherOptionOperation):
    option_name: str
    index_together: set[tuple[str, ...]] | None
    def __init__(self, name: str, index_together: _OptionTogetherT | None) -> None: ...

class AlterOrderWithRespectTo(ModelOptionOperation):
    option_name: str
    order_with_respect_to: str
    def __init__(self, name: str, order_with_respect_to: str) -> None: ...

class AlterModelOptions(ModelOptionOperation):
    ALTER_OPTION_KEYS: list[str]
    options: dict[str, Any]
    def __init__(self, name: str, options: dict[str, Any]) -> None: ...

class AlterModelManagers(ModelOptionOperation):
    managers: Sequence[tuple[str, Manager]]
    def __init__(self, name: str, managers: Sequence[tuple[str, Manager]]) -> None: ...

class IndexOperation(Operation):
    option_name: str
    @cached_property
    def model_name_lower(self) -> str: ...

class AddIndex(IndexOperation):
    model_name: str
    index: Index
    def __init__(self, model_name: str, index: Index) -> None: ...

class RemoveIndex(IndexOperation):
    model_name: str
    name: str
    def __init__(self, model_name: str, name: str) -> None: ...

class RenameIndex(IndexOperation):
    model_name: str
    new_name: str
    old_name: str | None
    old_fields: Sequence[str] | None
    def __init__(
        self,
        model_name: str,
        new_name: str,
        old_name: str | None = None,
        old_fields: Sequence[str] | None = None,
    ) -> None: ...
    @cached_property
    def old_name_lower(self) -> str: ...
    @cached_property
    def new_name_lower(self) -> str: ...

class AddConstraint(IndexOperation):
    def __init__(self, model_name: str, constraint: BaseConstraint) -> None: ...

class RemoveConstraint(IndexOperation):
    def __init__(self, model_name: str, name: str) -> None: ...
