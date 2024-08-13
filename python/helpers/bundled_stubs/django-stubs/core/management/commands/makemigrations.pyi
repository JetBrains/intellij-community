from typing import Any

from django.core.management.base import BaseCommand, OutputWrapper
from django.db.migrations.loader import MigrationLoader

class Command(BaseCommand):
    verbosity: int
    interactive: bool
    dry_run: bool
    merge: bool
    empty: bool
    migration_name: str
    include_header: bool
    scriptable: bool
    update: bool
    @property
    def log_output(self) -> OutputWrapper: ...
    def log(self, msg: str) -> None: ...
    def write_to_last_migration_files(self, changes: dict[str, Any]) -> None: ...
    def write_migration_files(
        self, changes: dict[str, Any], update_previous_migration_paths: dict[str, str] | None = ...
    ) -> None: ...
    @staticmethod
    def get_relative_path(path: str) -> str: ...
    def handle_merge(self, loader: MigrationLoader, conflicts: dict[str, Any]) -> None: ...
