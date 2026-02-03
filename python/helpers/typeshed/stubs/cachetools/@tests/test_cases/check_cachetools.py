from __future__ import annotations

from collections.abc import Hashable
from typing import Any
from typing_extensions import assert_type

from cachetools import LRUCache, cached, keys as cachekeys
from cachetools.func import fifo_cache, lfu_cache, lru_cache, rr_cache, ttl_cache

# Tests for cachetools.cached

# Explicitly parameterize the cache to avoid Unknown types
cache_inst: LRUCache[int, int] = LRUCache(maxsize=128)


@cached(cache_inst)
def check_cached(x: int) -> int:
    return x * 2


assert_type(check_cached(3), int)
# Methods cache_info/cache_clear are only present when info=True; do not access them here.


@cached(cache_inst, info=True)
def check_cached_with_info(x: int) -> int:
    return x + 1


assert_type(check_cached_with_info(4), int)
assert_type(check_cached_with_info.cache_info().misses, int)
check_cached_with_info.cache_clear()


# Tests for cachetools.func decorators


@lru_cache
def lru_noparens(x: int) -> int:
    return x * 2


@lru_cache(maxsize=32)
def lru_with_maxsize(x: int) -> int:
    return x * 3


assert_type(lru_noparens(3), int)
assert_type(lru_with_maxsize(3), int)
assert_type(lru_noparens.cache_info().hits, int)
assert_type(lru_with_maxsize.cache_info().misses, int)
assert_type(lru_with_maxsize.cache_parameters(), dict[str, Any])
lru_with_maxsize.cache_clear()


@fifo_cache
def fifo_func(x: int) -> int:
    return x


@lfu_cache
def lfu_func(x: int) -> int:
    return x


@rr_cache
def rr_func(x: int) -> int:
    return x


@ttl_cache
def ttl_func(x: int) -> int:
    return x


assert_type(fifo_func(1), int)
assert_type(lfu_func(1), int)
assert_type(rr_func(1), int)
assert_type(ttl_func(1), int)
assert_type(fifo_func.cache_info().currsize, int)
assert_type(lfu_func.cache_parameters(), dict[str, Any])


# Tests for cachetools.keys

k1 = cachekeys.hashkey(1, "a")
assert_type(k1, tuple[Hashable, ...])


class C:
    def method(self, a: int) -> int:
        return a


inst = C()

k2 = cachekeys.methodkey(inst, 5)
assert_type(k2, tuple[Hashable, ...])

k3 = cachekeys.typedkey(1, "x")
assert_type(k3, tuple[Hashable, ...])

k4 = cachekeys.typedmethodkey(inst, 2)
assert_type(k4, tuple[Hashable, ...])
