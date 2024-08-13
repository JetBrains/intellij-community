from typing import Any, Literal, type_check_only

from django.utils.functional import LazyObject
from typing_extensions import Self

# explicit dependency on standard settings to make it loaded
from . import global_settings  # noqa: F401

ENVIRONMENT_VARIABLE: Literal["DJANGO_SETTINGS_MODULE"]
DEFAULT_STORAGE_ALIAS: Literal["default"]
STATICFILES_STORAGE_ALIAS: Literal["staticfiles"]

# RemovedInDjango60Warning.
FORMS_URLFIELD_ASSUME_HTTPS_DEPRECATED_MSG: str

# required for plugin to be able to distinguish this specific instance of LazySettings from others
@type_check_only
class _DjangoConfLazyObject(LazyObject):
    def __getattr__(self, item: Any) -> Any: ...

class LazySettings(_DjangoConfLazyObject):
    SETTINGS_MODULE: str
    @property
    def configured(self) -> bool: ...
    def configure(self, default_settings: Any = ..., **options: Any) -> None: ...

settings: LazySettings

class Settings:
    SETTINGS_MODULE: str
    def __init__(self, settings_module: str) -> None: ...
    def is_overridden(self, setting: str) -> bool: ...

class UserSettingsHolder:
    SETTINGS_MODULE: None
    def __init__(self, default_settings: Any) -> None: ...
    def __getattr__(self, name: str) -> Any: ...
    def __setattr__(self, name: str, value: Any) -> None: ...
    def __delattr__(self, name: str) -> None: ...
    def is_overridden(self, setting: str) -> bool: ...

class SettingsReference(str):
    def __new__(self, value: Any, setting_name: str) -> Self: ...
    def __init__(self, value: str, setting_name: str) -> None: ...
