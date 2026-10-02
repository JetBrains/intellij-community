import random
from collections.abc import Callable, Iterator, MutableMapping, Sequence
from contextlib import AbstractContextManager
from typing import Any, Final, Generic, Literal, NamedTuple, Protocol, TypeVar, overload, type_check_only

__all__: Final = ("Cache", "FIFOCache", "LFUCache", "LRUCache", "RRCache", "TLRUCache", "TTLCache", "cached", "cachedmethod")
__version__: str

_KT = TypeVar("_KT")
_VT = TypeVar("_VT")
_TT = TypeVar("_TT", default=float)
_T = TypeVar("_T")
_R = TypeVar("_R")
_KT2 = TypeVar("_KT2")
_VT2 = TypeVar("_VT2")

class Cache(MutableMapping[_KT, _VT]):
    def __init__(self, maxsize: float, getsizeof: Callable[[_VT], float] | None = None) -> None: ...
    def __getitem__(self, key: _KT) -> _VT: ...
    def __setitem__(self, key: _KT, value: _VT) -> None: ...
    def __delitem__(self, key: _KT) -> None: ...
    def __missing__(self, key: _KT) -> _VT: ...
    def __iter__(self) -> Iterator[_KT]: ...
    def __len__(self) -> int: ...

    @overload
    def pop(self, key: _KT) -> _VT: ...
    @overload
    def pop(self, key: _KT, default: _VT | _T) -> _VT | _T: ...

    def setdefault(self, key: _KT, default: _VT | None = None) -> _VT: ...
    @property
    def maxsize(self) -> float: ...
    @property
    def currsize(self) -> float: ...
    @staticmethod
    def getsizeof(value: _VT) -> float: ...

class FIFOCache(Cache[_KT, _VT]): ...
class LFUCache(Cache[_KT, _VT]): ...
class LRUCache(Cache[_KT, _VT]): ...

class RRCache(Cache[_KT, _VT]):
    def __init__(
        self,
        maxsize: float,
        choice: Callable[[Sequence[_KT]], _KT] = random.choice,
        getsizeof: Callable[[_VT], float] | None = None,
    ) -> None: ...
    @property
    def choice(self) -> Callable[[Sequence[_KT]], _KT]: ...

class _TimedCache(Cache[_KT, _VT], Generic[_KT, _VT, _TT]):
    def __init__(self, maxsize: float, timer: Callable[[], _TT], getsizeof: Callable[[_VT], float] | None = None) -> None: ...

    class _Timer(AbstractContextManager[_T]):
        def __init__(self, timer: Callable[[], _T]) -> None: ...
        def __call__(self) -> _T: ...
        def __enter__(self) -> _T: ...
        def __exit__(self, *exc: object) -> None: ...
        def __getattr__(self, name: str) -> Any: ...

    @property
    def timer(self) -> _Timer[_TT]: ...

class TTLCache(_TimedCache[_KT, _VT, _TT]):
    @overload
    def __init__(
        self: TTLCache[_KT2, _VT2, float], maxsize: float, ttl: float, *, getsizeof: Callable[[_VT2], float] | None = None
    ) -> None: ...
    @overload
    def __init__(
        self,
        maxsize: float,
        ttl: Any,  # FIXME: must be "addable" to _TT
        timer: Callable[[], _TT],
        getsizeof: Callable[[_VT], float] | None = None,
    ) -> None: ...

    @property
    def ttl(self) -> Any: ...
    def expire(self, time: _TT | None = None) -> list[tuple[_KT, _VT]]: ...

class TLRUCache(_TimedCache[_KT, _VT, _TT]):
    @overload
    def __init__(
        self: TLRUCache[_KT2, _VT2, float],
        maxsize: float,
        ttu: Callable[[_KT2, _VT2, float], float],
        *,
        getsizeof: Callable[[_VT2], float] | None = None,
    ) -> None: ...
    @overload
    def __init__(
        self,
        maxsize: float,
        ttu: Callable[[_KT, _VT, _TT], _TT],
        timer: Callable[[], _TT],
        getsizeof: Callable[[_VT], float] | None = None,
    ) -> None: ...

    @property
    def ttu(self) -> Callable[[_KT, _VT, _TT], _TT]: ...
    def expire(self, time: _TT | None = None) -> list[tuple[_KT, _VT]]: ...

class _CacheInfo(NamedTuple):
    hits: int
    misses: int
    maxsize: float | None
    currsize: float

@type_check_only
class _AbstractCondition(AbstractContextManager[Any], Protocol):
    def wait(self, timeout: float | None = None) -> bool: ...
    def wait_for(self, predicate: Callable[[], _T], timeout: float | None = None) -> _T: ...
    def notify(self, n: int = 1) -> None: ...
    def notify_all(self) -> None: ...

@type_check_only
class _cached_wrapper(Generic[_R]):
    __wrapped__: Callable[..., _R]
    __name__: str
    __doc__: str | None
    cache: MutableMapping[Any, Any] | None
    cache_key: Callable[..., Any] = ...
    cache_lock: AbstractContextManager[Any] | None = None
    cache_condition: _AbstractCondition | None = None
    def __call__(self, /, *args: Any, **kwargs: Any) -> _R: ...
    def cache_clear(self) -> None: ...

@type_check_only
class _cached_wrapper_info(_cached_wrapper[_R]):
    def cache_info(self) -> _CacheInfo: ...

@overload
def cached(
    cache: MutableMapping[_KT, Any] | None,
    key: Callable[..., _KT] = ...,
    lock: AbstractContextManager[Any] | None = None,
    condition: _AbstractCondition | None = None,
    info: Literal[True] = ...,
) -> Callable[[Callable[..., _R]], _cached_wrapper_info[_R]]: ...
@overload
def cached(
    cache: MutableMapping[_KT, Any] | None,
    key: Callable[..., _KT] = ...,
    lock: AbstractContextManager[Any] | None = None,
    condition: _AbstractCondition | None = None,
    info: Literal[False] = ...,
) -> Callable[[Callable[..., _R]], _cached_wrapper[_R]]: ...

@type_check_only
class _cachedmethod_wrapper(Generic[_R]):
    __wrapped__: Callable[..., _R]
    __name__: str
    __doc__: str | None
    cache: MutableMapping[Any, Any] | None
    cache_key: Callable[..., Any] = ...
    cache_lock: AbstractContextManager[Any] | None = None
    cache_condition: _AbstractCondition | None = None
    def __call__(self, /, *args: Any, **kwargs: Any) -> _R: ...
    def cache_clear(self) -> None: ...

@type_check_only
class _cachedmethod_wrapper_info(_cachedmethod_wrapper[_R]):
    def cache_info(self) -> _CacheInfo: ...

@overload
def cachedmethod(
    cache: Callable[[Any], MutableMapping[_KT, Any]],
    key: Callable[..., _KT] = ...,
    lock: Callable[[Any], AbstractContextManager[Any]] | None = None,
    condition: Callable[[Any], _AbstractCondition] | None = None,
    info: Literal[True] = ...,
) -> Callable[[Callable[..., _R]], _cachedmethod_wrapper_info[_R]]: ...
@overload
def cachedmethod(
    cache: Callable[[Any], MutableMapping[_KT, Any]],
    key: Callable[..., _KT] = ...,
    lock: Callable[[Any], AbstractContextManager[Any]] | None = None,
    condition: Callable[[Any], _AbstractCondition] | None = None,
    info: Literal[False] = ...,
) -> Callable[[Callable[..., _R]], _cachedmethod_wrapper[_R]]: ...
