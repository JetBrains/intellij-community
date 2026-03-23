from typing import Any

from django.core.management.base import BaseCommand
from typing_extensions import override

class Command(BaseCommand):
    output_transaction: bool
    @override
    def execute(self, *args: Any, **options: Any) -> str | None: ...
