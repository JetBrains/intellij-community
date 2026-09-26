from typing import Any

from django.core.management.base import BaseCommand
from typing_extensions import override

class Command(BaseCommand):
    verbosity: int
    @override
    def handle(self, *tablenames: list[str], **options: Any) -> None: ...
    def create_table(self, database: str, tablename: str, dry_run: bool) -> None: ...
