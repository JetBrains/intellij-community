from typing import Any

from django.core.management.base import BaseCommand
from typing_extensions import override

class Command(BaseCommand):
    missing_args_message: str
    @override
    def handle(self, *args: Any, using: str | None = None, **kwargs: Any) -> None: ...
