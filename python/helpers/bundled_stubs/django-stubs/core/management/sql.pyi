from typing import Any

from django.core.management.color import Style
from django.db.backends.base.base import BaseDatabaseWrapper

def sql_flush(
    style: Style,
    connection: BaseDatabaseWrapper,
    reset_sequences: bool = ...,
    allow_cascade: bool = ...,
) -> list[str]: ...
def emit_pre_migrate_signal(verbosity: int, interactive: bool, db: str, **kwargs: Any) -> None: ...
def emit_post_migrate_signal(verbosity: int, interactive: bool, db: str, **kwargs: Any) -> None: ...
