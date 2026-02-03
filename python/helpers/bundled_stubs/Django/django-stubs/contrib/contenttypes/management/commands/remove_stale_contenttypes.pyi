from typing import Any, Literal

from _typeshed import Unused
from django.core.management import BaseCommand
from django.db.models.deletion import Collector

class Command(BaseCommand):
    def handle(self, **options: Any) -> None: ...

class NoFastDeleteCollector(Collector):
    def can_fast_delete(self, *args: Unused, **kwargs: Unused) -> Literal[False]: ...
