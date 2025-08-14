from collections.abc import Container
from typing import Any

from django.core.management.base import BaseCommand
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.migrations.operations.base import Operation

class Command(BaseCommand):
    verbosity: int
    interactive: bool
    start: float
    def migration_progress_callback(self, action: str, migration: Any | None = None, fake: bool = False) -> None: ...
    def sync_apps(self, connection: BaseDatabaseWrapper, app_labels: Container[str]) -> None: ...
    @staticmethod
    def describe_operation(operation: Operation, backwards: bool) -> tuple[str, bool]: ...
