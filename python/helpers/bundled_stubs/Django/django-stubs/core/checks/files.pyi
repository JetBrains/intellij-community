from collections.abc import Sequence
from typing import Any

from django.apps.config import AppConfig
from django.core.checks.messages import Error

def check_setting_file_upload_temp_dir(app_configs: Sequence[AppConfig] | None, **kwargs: Any) -> Sequence[Error]: ...
