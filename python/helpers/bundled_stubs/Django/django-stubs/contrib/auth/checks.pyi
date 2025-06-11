from collections.abc import Sequence
from typing import Any

from django.apps.config import AppConfig
from django.core.checks.messages import CheckMessage

def check_user_model(app_configs: Sequence[AppConfig] | None = ..., **kwargs: Any) -> Sequence[CheckMessage]: ...
def check_models_permissions(
    app_configs: Sequence[AppConfig] | None = ..., **kwargs: Any
) -> Sequence[CheckMessage]: ...
