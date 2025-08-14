from typing import Any

from django.utils.connection import BaseConnectionHandler

from .backends.base import BaseCache as BaseCache
from .backends.base import CacheKeyWarning as CacheKeyWarning
from .backends.base import InvalidCacheBackendError as InvalidCacheBackendError
from .backends.base import InvalidCacheKey as InvalidCacheKey

DEFAULT_CACHE_ALIAS: str

class CacheHandler(BaseConnectionHandler[BaseCache]):
    settings_name: str
    exception_class: type[Exception]
    def create_connection(self, alias: str) -> BaseCache: ...
    def all(self, initialized_only: bool = False) -> list[BaseCache]: ...

def close_caches(**kwargs: Any) -> None: ...

caches: CacheHandler
# Actually ConnectionProxy, but quacks exactly like BaseCache, it's not worth distinguishing the two.
cache: BaseCache
