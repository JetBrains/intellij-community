from collections.abc import Sequence
from types import ModuleType
from typing import Any

from django.core.cache.backends.base import BaseCache
from typing_extensions import override

class BaseMemcachedCache(BaseCache):
    def __init__(
        self,
        server: str | Sequence[str],
        params: dict[str, Any],
        library: ModuleType,
        value_not_found_exception: type[BaseException],
    ) -> None: ...
    @property
    def client_servers(self) -> Sequence[str]: ...

class PyLibMCCache(BaseMemcachedCache):
    def __init__(self, server: str | Sequence[str], params: dict[str, Any]) -> None: ...
    @property
    @override
    def client_servers(self) -> list[str]: ...

class PyMemcacheCache(BaseMemcachedCache):
    def __init__(self, server: str | Sequence[str], params: dict[str, Any]) -> None: ...
