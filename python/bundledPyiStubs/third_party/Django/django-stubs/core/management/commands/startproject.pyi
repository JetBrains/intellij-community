from typing import Any

from django.core.management.templates import TemplateCommand
from typing_extensions import override

class Command(TemplateCommand):
    missing_args_message: str

    @override
    def handle(self, **options: Any) -> None: ...  # type: ignore[override]
