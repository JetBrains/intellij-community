from typing import Any, ClassVar

from django.core.cache.backends.base import BaseCache
from django.utils.functional import _StrOrPromise

class Options:
    db_table: str
    app_label: str
    model_name: str
    verbose_name: _StrOrPromise
    verbose_name_plural: _StrOrPromise
    object_name: str
    abstract: bool
    managed: bool
    proxy: bool
    swapped: bool
    def __init__(self, table: str) -> None: ...

class BaseDatabaseCache(BaseCache):
    cache_model_class: Any
    def __init__(self, table: str, params: dict[str, Any]) -> None: ...

class DatabaseCache(BaseDatabaseCache):
    pickle_protocol: ClassVar[int]
