from typing import Any

from django.core.management.base import LabelCommand
from typing_extensions import override

class Command(LabelCommand):
    @override
    def handle_label(self, path: str, **options: Any) -> str | None: ...  # type: ignore[override]
