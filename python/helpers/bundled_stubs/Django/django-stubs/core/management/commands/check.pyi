from typing import Any

from django.core.management.base import BaseCommand
from typing_extensions import override

class Command(BaseCommand):
    @override
    def handle(self, *app_labels: list[str], **options: Any) -> None: ...
