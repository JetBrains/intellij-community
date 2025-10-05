from _typeshed import Incomplete
from typing import Any

UNDEFINED: Any

class Key:
    def __new__(cls, *path_args, **kwargs): ...
    def __hash__(self) -> int: ...
    def __eq__(self, other): ...
    def __ne__(self, other): ...
    def __lt__(self, other): ...
    def __le__(self, other): ...
    def __gt__(self, other): ...
    def __ge__(self, other): ...
    def __getnewargs__(self): ...
    def parent(self): ...
    def root(self): ...
    def namespace(self): ...
    def project(self): ...
    app: Any
    def database(self) -> str | None: ...
    def id(self): ...
    def string_id(self): ...
    def integer_id(self): ...
    def pairs(self): ...
    def flat(self): ...
    def kind(self): ...
    def reference(self): ...
    def serialized(self): ...
    def urlsafe(self): ...
    def to_legacy_urlsafe(self, location_prefix): ...
    def get(
        self,
        read_consistency: Incomplete | None = ...,
        read_policy: Incomplete | None = ...,
        transaction: Incomplete | None = ...,
        retries: Incomplete | None = ...,
        timeout: Incomplete | None = ...,
        deadline: Incomplete | None = ...,
        use_cache: Incomplete | None = ...,
        use_global_cache: Incomplete | None = ...,
        use_datastore: Incomplete | None = ...,
        global_cache_timeout: Incomplete | None = ...,
        use_memcache: Incomplete | None = ...,
        memcache_timeout: Incomplete | None = ...,
        max_memcache_items: Incomplete | None = ...,
        force_writes: Incomplete | None = ...,
        _options: Incomplete | None = ...,
    ): ...
    def get_async(
        self,
        read_consistency: Incomplete | None = ...,
        read_policy: Incomplete | None = ...,
        transaction: Incomplete | None = ...,
        retries: Incomplete | None = ...,
        timeout: Incomplete | None = ...,
        deadline: Incomplete | None = ...,
        use_cache: Incomplete | None = ...,
        use_global_cache: Incomplete | None = ...,
        use_datastore: Incomplete | None = ...,
        global_cache_timeout: Incomplete | None = ...,
        use_memcache: Incomplete | None = ...,
        memcache_timeout: Incomplete | None = ...,
        max_memcache_items: Incomplete | None = ...,
        force_writes: Incomplete | None = ...,
        _options: Incomplete | None = ...,
    ): ...
    def delete(
        self,
        retries: Incomplete | None = ...,
        timeout: Incomplete | None = ...,
        deadline: Incomplete | None = ...,
        use_cache: Incomplete | None = ...,
        use_global_cache: Incomplete | None = ...,
        use_datastore: Incomplete | None = ...,
        global_cache_timeout: Incomplete | None = ...,
        use_memcache: Incomplete | None = ...,
        memcache_timeout: Incomplete | None = ...,
        max_memcache_items: Incomplete | None = ...,
        force_writes: Incomplete | None = ...,
        _options: Incomplete | None = ...,
    ): ...
    def delete_async(
        self,
        retries: Incomplete | None = ...,
        timeout: Incomplete | None = ...,
        deadline: Incomplete | None = ...,
        use_cache: Incomplete | None = ...,
        use_global_cache: Incomplete | None = ...,
        use_datastore: Incomplete | None = ...,
        global_cache_timeout: Incomplete | None = ...,
        use_memcache: Incomplete | None = ...,
        memcache_timeout: Incomplete | None = ...,
        max_memcache_items: Incomplete | None = ...,
        force_writes: Incomplete | None = ...,
        _options: Incomplete | None = ...,
    ): ...
    @classmethod
    def from_old_key(cls, old_key) -> None: ...
    def to_old_key(self) -> None: ...
