from typing import Any

from django.core.management.templates import TemplateCommand

class Command(TemplateCommand):
    missing_args_message: str

    def handle(self, **options: Any) -> None: ...  # type: ignore[override]
