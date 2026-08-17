from typing import Any

from django.core.management import BaseCommand
from typing_extensions import override

class Command(BaseCommand):
    @override
    def handle(self, **options: Any) -> None: ...
