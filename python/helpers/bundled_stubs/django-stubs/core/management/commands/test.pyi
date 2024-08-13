from typing import Any

from django.core.management.base import BaseCommand

class Command(BaseCommand):
    test_runner: Any
    def run_from_argv(self, argv: Any) -> None: ...
    def add_arguments(self, parser: Any) -> None: ...
