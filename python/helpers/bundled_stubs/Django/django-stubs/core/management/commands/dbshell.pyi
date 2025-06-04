from typing import Any

from django.core.management.base import BaseCommand

class Command(BaseCommand):
    def handle(self, **options: Any) -> None: ...
