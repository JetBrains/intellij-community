from collections.abc import Sequence

from django.apps.config import AppConfig
from django.core.checks.messages import CheckMessage

def check_generic_foreign_keys(
    app_configs: Sequence[AppConfig] | None, *, databases: Sequence[str] | None = ..., **kwargs: object
) -> list[CheckMessage]: ...
def check_model_name_lengths(
    app_configs: Sequence[AppConfig] | None, *, databases: Sequence[str] | None = ..., **kwargs: object
) -> list[CheckMessage]: ...
