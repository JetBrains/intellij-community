from collections.abc import Sequence

from django.apps.config import AppConfig
from django.core.checks import CheckMessage

def check_mailers_default_alias(app_configs: Sequence[AppConfig] | None, **kwargs: object) -> list[CheckMessage]: ...

NON_PRODUCTION_EMAIL_BACKENDS: set[str]

def check_mailers_production_backend(
    app_configs: Sequence[AppConfig] | None, **kwargs: object
) -> list[CheckMessage]: ...
