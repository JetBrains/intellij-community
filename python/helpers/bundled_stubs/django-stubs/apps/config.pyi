import types
from collections.abc import Iterator

from django.apps.registry import Apps
from django.db.models.base import Model
from django.utils.functional import _Getter, _StrOrPromise

APPS_MODULE_NAME: str
MODELS_MODULE_NAME: str

class AppConfig:
    name: str
    module: types.ModuleType | None
    apps: Apps | None
    label: str
    verbose_name: _StrOrPromise
    path: str
    models_module: types.ModuleType | None
    # Default auto_field is a cached_property on the base, but is usually subclassed as a str
    # If not subclassing with a str, a type ignore[override] is needed
    models: dict[str, type[Model]]
    default: bool
    default_auto_field: str | _Getter[str]
    def __init__(self, app_name: str, app_module: types.ModuleType | None) -> None: ...
    @classmethod
    def create(cls, entry: str) -> AppConfig: ...
    def get_model(self, model_name: str, require_ready: bool = ...) -> type[Model]: ...
    def get_models(self, include_auto_created: bool = ..., include_swapped: bool = ...) -> Iterator[type[Model]]: ...
    def import_models(self) -> None: ...
    def ready(self) -> None: ...
