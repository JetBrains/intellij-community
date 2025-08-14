from collections.abc import Sequence

from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.migrations.operations.base import Operation
from django.db.migrations.state import ProjectState
from typing_extensions import Self

class Migration:
    operations: Sequence[Operation]
    dependencies: Sequence[tuple[str, str]]
    run_before: Sequence[tuple[str, str]]
    replaces: Sequence[tuple[str, str]]
    initial: bool | None
    atomic: bool
    name: str
    app_label: str
    def __init__(self, name: str, app_label: str) -> None: ...
    def mutate_state(self, project_state: ProjectState, preserve: bool = True) -> ProjectState: ...
    def apply(
        self, project_state: ProjectState, schema_editor: BaseDatabaseSchemaEditor, collect_sql: bool = False
    ) -> ProjectState: ...
    def unapply(
        self, project_state: ProjectState, schema_editor: BaseDatabaseSchemaEditor, collect_sql: bool = False
    ) -> ProjectState: ...

class SwappableTuple(tuple[str, str]):
    setting: str
    def __new__(cls, value: tuple[str, str], setting: str) -> Self: ...

def swappable_dependency(value: str) -> SwappableTuple: ...
