from collections.abc import Sequence
from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.migrations.migration import Migration
from django.db.migrations.state import ProjectState

from .exceptions import AmbiguityError as AmbiguityError
from .exceptions import BadMigrationError as BadMigrationError
from .exceptions import InconsistentMigrationHistory as InconsistentMigrationHistory
from .exceptions import NodeNotFoundError as NodeNotFoundError

MIGRATIONS_MODULE_NAME: str

class MigrationLoader:
    connection: BaseDatabaseWrapper | None
    disk_migrations: dict[tuple[str, str], Migration]
    applied_migrations: dict[tuple[str, str], Migration]
    ignore_no_migrations: bool
    def __init__(
        self,
        connection: BaseDatabaseWrapper | None,
        load: bool = True,
        ignore_no_migrations: bool = False,
        replace_migrations: bool = True,
    ) -> None: ...
    @classmethod
    def migrations_module(cls, app_label: str) -> tuple[str | None, bool]: ...
    unmigrated_apps: set[str]
    migrated_apps: set[str]
    def load_disk(self) -> None: ...
    def get_migration(self, app_label: str, name_prefix: str) -> Migration: ...
    def get_migration_by_prefix(self, app_label: str, name_prefix: str) -> Migration: ...
    def check_key(self, key: tuple[str, str], current_app: str) -> tuple[str, str] | None: ...
    def add_internal_dependencies(self, key: tuple[str, str], migration: Migration) -> None: ...
    def add_external_dependencies(self, key: tuple[str, str], migration: Migration) -> None: ...
    graph: Any
    replacements: Any
    def build_graph(self) -> None: ...
    def check_consistent_history(self, connection: BaseDatabaseWrapper) -> None: ...
    def detect_conflicts(self) -> dict[str, list[str]]: ...
    def project_state(
        self, nodes: tuple[str, str] | Sequence[tuple[str, str]] | None = None, at_end: bool = True
    ) -> ProjectState: ...
