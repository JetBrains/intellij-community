from collections.abc import Sequence
from typing import Any, TextIO

from django.apps.config import AppConfig
from django.apps.registry import Apps
from django.db.migrations.migration import Migration

def create_permissions(
    app_config: AppConfig,
    verbosity: int = ...,
    interactive: bool = ...,
    using: str = ...,
    apps: Apps = ...,
    **kwargs: Any,
) -> None: ...
def rename_permissions_after_model_rename(
    app_config: AppConfig,
    verbosity: int = ...,
    plan: Sequence[tuple[Migration, bool]] | None = ...,
    using: str = ...,
    apps: Apps = ...,
    stdout: TextIO = ...,
    **kwargs: Any,
) -> None: ...
def get_system_username() -> str: ...
def get_default_username(check_db: bool = ..., database: str = ...) -> str: ...
