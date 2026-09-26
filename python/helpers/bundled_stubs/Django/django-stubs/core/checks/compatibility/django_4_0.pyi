from collections.abc import Sequence

from django.apps.config import AppConfig
from django.core.checks.messages import CheckMessage

def check_csrf_trusted_origins(app_configs: Sequence[AppConfig] | None, **kwargs: object) -> list[CheckMessage]: ...
