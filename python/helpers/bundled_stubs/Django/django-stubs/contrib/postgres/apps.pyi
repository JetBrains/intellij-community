from typing import Any

from django.apps import AppConfig

RANGE_TYPES: Any

def uninstall_if_needed(setting: Any, value: Any, enter: Any, **kwargs: Any) -> None: ...

class PostgresConfig(AppConfig):
    name: str
    verbose_name: Any
    def ready(self) -> None: ...
