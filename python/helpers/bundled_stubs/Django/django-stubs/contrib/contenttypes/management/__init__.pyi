from typing import Any

from django.apps.config import AppConfig
from django.apps.registry import Apps
from django.contrib.contenttypes.models import ContentType
from django.db import migrations
from django.db.backends.sqlite3.schema import DatabaseSchemaEditor
from django.db.migrations.migration import Migration
from django.db.migrations.state import StateApps
from django.db.models.base import Model

class RenameContentType(migrations.RunPython):
    app_label: str
    old_model: str
    new_model: str
    def __init__(self, app_label: str, old_model: str, new_model: str) -> None: ...
    def rename_forward(self, apps: StateApps, schema_editor: DatabaseSchemaEditor) -> None: ...
    def rename_backward(self, apps: StateApps, schema_editor: DatabaseSchemaEditor) -> None: ...

def inject_rename_contenttypes_operations(
    plan: list[tuple[Migration, bool]] | None = None, apps: StateApps = ..., using: str = "default", **kwargs: Any
) -> None: ...
def get_contenttypes_and_models(
    app_config: AppConfig, using: str, ContentType: type[ContentType]
) -> tuple[dict[str, ContentType], dict[str, type[Model]]]: ...
def create_contenttypes(
    app_config: AppConfig,
    verbosity: int = 2,
    interactive: bool = True,
    using: str = "default",
    apps: Apps = ...,
    **kwargs: Any,
) -> None: ...
