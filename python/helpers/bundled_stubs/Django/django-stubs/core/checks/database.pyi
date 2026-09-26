from collections.abc import Iterable

from django.core.checks import CheckMessage

def check_database_backends(databases: Iterable[str] | None = None, **kwargs: object) -> list[CheckMessage]: ...
