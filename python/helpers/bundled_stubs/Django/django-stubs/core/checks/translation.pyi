from collections.abc import Sequence

from django.apps.config import AppConfig
from django.core.checks.messages import CheckMessage, Error

E001: Error
E002: Error
E003: Error
E004: Error

def check_setting_language_code(app_configs: Sequence[AppConfig] | None, **kwargs: object) -> list[CheckMessage]: ...
def check_setting_languages(app_configs: Sequence[AppConfig] | None, **kwargs: object) -> list[CheckMessage]: ...
def check_setting_languages_bidi(app_configs: Sequence[AppConfig] | None, **kwargs: object) -> list[CheckMessage]: ...
def check_language_settings_consistent(
    app_configs: Sequence[AppConfig] | None, **kwargs: object
) -> list[CheckMessage]: ...
