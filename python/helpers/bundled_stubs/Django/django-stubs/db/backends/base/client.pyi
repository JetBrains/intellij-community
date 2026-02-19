from collections.abc import Iterable, Sequence
from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper

class BaseDatabaseClient:
    executable_name: str | None
    connection: BaseDatabaseWrapper
    def __init__(self, connection: BaseDatabaseWrapper) -> None: ...
    @classmethod
    def settings_to_cmd_args_env(
        cls,
        settings_dict: dict[str, Any],
        parameters: Iterable[str],
    ) -> tuple[Sequence[str], dict[str, str] | None]: ...
    def runshell(self, parameters: Iterable[str]) -> None: ...
