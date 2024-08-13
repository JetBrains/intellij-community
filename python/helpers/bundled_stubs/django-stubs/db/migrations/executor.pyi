from collections.abc import Sequence
from typing import Protocol, type_check_only

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.migrations.migration import Migration

from .loader import MigrationLoader
from .recorder import MigrationRecorder
from .state import ProjectState

@type_check_only
class _ProgressCallbackT(Protocol):
    def __call__(self, action: str, migration: Migration | None = ..., fake: bool | None = ..., /) -> None: ...

class MigrationExecutor:
    connection: BaseDatabaseWrapper
    loader: MigrationLoader
    recorder: MigrationRecorder
    progress_callback: _ProgressCallbackT | None
    def __init__(
        self,
        connection: BaseDatabaseWrapper | None,
        progress_callback: _ProgressCallbackT | None = ...,
    ) -> None: ...
    def migration_plan(
        self, targets: Sequence[tuple[str, str | None]] | set[tuple[str, str]], clean_start: bool = ...
    ) -> list[tuple[Migration, bool]]: ...
    def migrate(
        self,
        targets: Sequence[tuple[str, str | None]] | None,
        plan: Sequence[tuple[Migration, bool]] | None = ...,
        state: ProjectState | None = ...,
        fake: bool = ...,
        fake_initial: bool = ...,
    ) -> ProjectState: ...
    def apply_migration(
        self, state: ProjectState, migration: Migration, fake: bool = ..., fake_initial: bool = ...
    ) -> ProjectState: ...
    def record_migration(self, migration: Migration) -> None: ...
    def unapply_migration(self, state: ProjectState, migration: Migration, fake: bool = ...) -> ProjectState: ...
    def check_replacements(self) -> None: ...
    def detect_soft_applied(
        self, project_state: ProjectState | None, migration: Migration
    ) -> tuple[bool, ProjectState]: ...
