from collections.abc import Sequence

from django.apps.config import AppConfig
from django.core.checks.messages import CheckMessage

def check_setting_file_upload_temp_dir(
    app_configs: Sequence[AppConfig] | None, **kwargs: object
) -> list[CheckMessage]: ...
