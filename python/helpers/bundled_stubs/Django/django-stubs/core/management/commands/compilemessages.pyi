from pathlib import Path
from typing import Any

from _typeshed import StrPath
from django.core.management.base import BaseCommand
from typing_extensions import override

def has_bom(fn: Path) -> bool: ...
def is_dir_writable(path: StrPath) -> bool: ...

class Command(BaseCommand):
    program: str
    program_options: list[str]
    verbosity: int
    has_errors: bool
    @override
    def handle(self, **options: Any) -> None: ...
    def compile_messages(self, locations: list[tuple[StrPath, StrPath]]) -> None: ...
