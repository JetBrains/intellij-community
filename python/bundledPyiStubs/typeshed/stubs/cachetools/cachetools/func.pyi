from collections.abc import Callable, Sequence
from typing import Any, Final, Generic, TypeVar, overload, type_check_only

from . import _CacheInfo

__all__: Final = ("fifo_cache", "lfu_cache", "lru_cache", "rr_cache", "ttl_cache")

_T = TypeVar("_T")
_R = TypeVar("_R")

@type_check_only
class _cachetools_cache_wrapper(Generic[_R]):
    __wrapped__: Callable[..., _R]
    __name__: str
    __doc__: str | None
    def __call__(self, /, *args: Any, **kwargs: Any) -> _R: ...
    def cache_info(self) -> _CacheInfo: ...
    def cache_clear(self) -> None: ...
    def cache_parameters(self) -> dict[str, Any]: ...

@overload
def fifo_cache(
    maxsize: int | None = 128, typed: bool = False
) -> Callable[[Callable[..., _R]], _cachetools_cache_wrapper[_R]]: ...
@overload
def fifo_cache(maxsize: Callable[..., _R], typed: bool = False) -> _cachetools_cache_wrapper[_R]: ...

@overload
def lfu_cache(maxsize: int | None = 128, typed: bool = False) -> Callable[[Callable[..., _R]], _cachetools_cache_wrapper[_R]]: ...
@overload
def lfu_cache(maxsize: Callable[..., _R], typed: bool = False) -> _cachetools_cache_wrapper[_R]: ...

@overload
def lru_cache(maxsize: int | None = 128, typed: bool = False) -> Callable[[Callable[..., _R]], _cachetools_cache_wrapper[_R]]: ...
@overload
def lru_cache(maxsize: Callable[..., _R], typed: bool = False) -> _cachetools_cache_wrapper[_R]: ...

@overload
def rr_cache(
    maxsize: int | None = 128, choice: Callable[[Sequence[_T]], _T] = ..., typed: bool = False
) -> Callable[[Callable[..., _R]], _cachetools_cache_wrapper[_R]]: ...
@overload
def rr_cache(
    maxsize: Callable[..., _R], choice: Callable[[Sequence[_T]], _T] = ..., typed: bool = False
) -> _cachetools_cache_wrapper[_R]: ...

@overload
def ttl_cache(
    maxsize: int | None = 128, ttl: Any = 600, timer: Callable[[], _T] = ..., typed: bool = False
) -> Callable[[Callable[..., _R]], _cachetools_cache_wrapper[_R]]: ...
@overload
def ttl_cache(
    maxsize: Callable[..., _R], ttl: Any = 600, timer: Callable[[], _T] = ..., typed: bool = False
) -> _cachetools_cache_wrapper[_R]: ...
