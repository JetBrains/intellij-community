from typing import Any

from django.core.management.base import BaseCommand
from typing_extensions import override

class Command(BaseCommand):
    test_runner: Any
    @override
    def run_from_argv(self, argv: Any) -> None: ...
    @override
    def add_arguments(self, parser: Any) -> None: ...
