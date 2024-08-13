from typing import Any

from django.core.management.base import BaseCommand

class Command(BaseCommand):
    def handle(self, *app_labels: list[str], **options: Any) -> None: ...
