from typing import Any

from django.core.management.base import LabelCommand

class Command(LabelCommand):
    def handle_label(self, path: str, **options: Any) -> str | None: ...  # type: ignore[override]
