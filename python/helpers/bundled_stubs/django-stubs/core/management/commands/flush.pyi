from typing import Any

from django.core.management.base import BaseCommand
from django.core.management.color import Style

class Command(BaseCommand):
    style: Style

    def handle(self, **options: Any) -> None: ...
