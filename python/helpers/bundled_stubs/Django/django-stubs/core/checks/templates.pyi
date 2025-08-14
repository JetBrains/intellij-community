from collections.abc import Sequence
from typing import Any

from django.apps.config import AppConfig
from django.core.checks.messages import Error, Warning

E001: Error
E002: Error
W003: Warning

def check_setting_app_dirs_loaders(app_configs: Sequence[AppConfig] | None, **kwargs: Any) -> Sequence[Error]: ...
def check_string_if_invalid_is_string(app_configs: Sequence[AppConfig] | None, **kwargs: Any) -> Sequence[Error]: ...
def check_for_template_tags_with_the_same_name(
    app_configs: Sequence[AppConfig] | None, **kwargs: Any
) -> Sequence[Warning]: ...
