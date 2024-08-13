from pathlib import Path
from typing import Any

from django.core.management.base import BaseCommand
from django.utils._os import _PathCompatible

def has_bom(fn: Path) -> bool: ...
def is_writable(path: _PathCompatible) -> bool: ...

class Command(BaseCommand):
    program: str
    program_options: list[str]
    verbosity: int
    has_errors: bool
    def handle(self, **options: Any) -> None: ...
    def compile_messages(self, locations: list[tuple[_PathCompatible, _PathCompatible]]) -> None: ...
