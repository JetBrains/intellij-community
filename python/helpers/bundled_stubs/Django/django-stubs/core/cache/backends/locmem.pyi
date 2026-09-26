from typing import Any, ClassVar

from django.core.cache.backends.base import BaseCache

class LocMemCache(BaseCache):
    pickle_protocol: ClassVar[int]

    def __init__(self, name: str, params: dict[str, Any]) -> None: ...
