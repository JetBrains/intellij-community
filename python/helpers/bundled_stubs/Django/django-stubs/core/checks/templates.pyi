from collections.abc import Sequence
from typing import Any

from django.apps.config import AppConfig
from django.core.checks.messages import CheckMessage

def check_templates(app_configs: Sequence[AppConfig] | None, **kwargs: Any) -> Sequence[CheckMessage]: ...
