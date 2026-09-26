from collections.abc import Iterable
from typing import Any

from django.db.backends.base.client import BaseDatabaseClient
from django.db.backends.mysql.base import DatabaseWrapper
from typing_extensions import override

class DatabaseClient(BaseDatabaseClient):
    connection: DatabaseWrapper
    executable_name: str
    @classmethod
    @override
    def settings_to_cmd_args_env(
        cls,
        settings_dict: dict[str, Any],
        parameters: Iterable[str],
    ) -> tuple[list[str], dict[str, str] | None]: ...
