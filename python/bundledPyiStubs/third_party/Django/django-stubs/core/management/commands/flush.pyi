from typing import Any

from django.core.management.base import BaseCommand
from django.core.management.color import Style
from typing_extensions import override

class Command(BaseCommand):
    style: Style

    @override
    def handle(self, **options: Any) -> None: ...
