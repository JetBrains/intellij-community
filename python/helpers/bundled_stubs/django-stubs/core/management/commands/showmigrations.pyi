from django.core.management.base import BaseCommand
from django.db.backends.base.base import BaseDatabaseWrapper

class Command(BaseCommand):
    verbosity: int
    def show_list(self, connection: BaseDatabaseWrapper, app_names: list[str] | None = ...) -> None: ...
    def show_plan(self, connection: BaseDatabaseWrapper, app_names: list[str] | None = ...) -> None: ...
