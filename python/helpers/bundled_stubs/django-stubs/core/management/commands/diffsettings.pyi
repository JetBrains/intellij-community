from collections.abc import Callable
from typing import Any

from django.core.management.base import BaseCommand

def module_to_dict(module: Any, omittable: Callable[[str], bool] = ...) -> dict[str, str]: ...

class Command(BaseCommand):
    def handle(self, **options: Any) -> str: ...
    def output_hash(
        self, user_settings: dict[str, str], default_settings: dict[str, str], **options: Any
    ) -> list[str]: ...
    def output_unified(
        self, user_settings: dict[str, str], default_settings: dict[str, str], **options: Any
    ) -> list[str]: ...
