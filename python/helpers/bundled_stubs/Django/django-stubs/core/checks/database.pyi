from collections.abc import Iterable, Sequence
from typing import Any

from django.core.checks import CheckMessage

def check_database_backends(databases: Iterable[str] | None = None, **kwargs: Any) -> Sequence[CheckMessage]: ...
