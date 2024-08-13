from collections.abc import Iterable
from typing import Any

from django.db.backends.base.client import BaseDatabaseClient
from django.db.backends.oracle.base import DatabaseWrapper

class DatabaseClient(BaseDatabaseClient):
    connection: DatabaseWrapper
    executable_name: str
    wrapper_name: str
    @staticmethod
    def connect_string(settings_dict: dict[str, Any]) -> str: ...
    @classmethod
    def settings_to_cmd_args_env(
        cls, settings_dict: dict[str, Any], parameters: Iterable[str]
    ) -> tuple[list[str], None]: ...
