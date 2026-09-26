from typing import Any

from django.core.checks import CheckMessage
from django.core.exceptions import ValidationError

def prefix_validation_error(error: Any, prefix: Any, code: Any, params: Any) -> ValidationError: ...

class CheckPostgresInstalledMixin:
    def _check_postgres_installed(self, *args: Any) -> list[CheckMessage]: ...
    def check(self, *args: Any, **kwargs: Any) -> list[CheckMessage]: ...
