# Stubs for functools (Python 3)

# NOTE: These are incomplete!

from abc import ABCMeta, abstractmethod
import sys
from typing import Any, Callable, Generic, Dict, Iterable, Mapping, Optional, Sequence, Tuple, TypeVar, NamedTuple, overload
from collections import namedtuple

_AnyCallable = Callable[..., Any]

_T = TypeVar("_T")
_S = TypeVar("_S")
@overload
def reduce(function: Callable[[_T, _S], _T],
           sequence: Iterable[_S], initial: _T) -> _T: ...
@overload
def reduce(function: Callable[[_T, _T], _T],
           sequence: Iterable[_T]) -> _T: ...


class CacheInfo(NamedTuple('CacheInfo', [
    ('hits', int), ('misses', int), ('maxsize', int), ('currsize', int)])
):
    ...

class _lru_cache_wrapper(Generic[_T]):
    __wrapped__ = ...  # type: Callable[..., _T]
    def __call__(self, *args: Any, **kwargs: Any) -> _T: ...
    def cache_info(self) -> CacheInfo: ...

class lru_cache():
    def __init__(self, maxsize: Optional[int] = ..., typed: bool = ...) -> None: ...
    def __call__(self, f: Callable[..., _T]) -> _lru_cache_wrapper[_T]: ...


WRAPPER_ASSIGNMENTS = ...  # type: Sequence[str]
WRAPPER_UPDATES = ...  # type: Sequence[str]

def update_wrapper(wrapper: _AnyCallable, wrapped: _AnyCallable, assigned: Sequence[str] = ...,
                   updated: Sequence[str] = ...) -> None: ...
def wraps(wrapped: _AnyCallable, assigned: Sequence[str] = ..., updated: Sequence[str] = ...) -> Callable[[_AnyCallable], _AnyCallable]: ...
def total_ordering(cls: type) -> type: ...
def cmp_to_key(mycmp: Callable[[_T, _T], int]) -> Callable[[_T], Any]: ...

class partial(Generic[_T]):
    func = ...  # type: Callable[..., _T]
    args = ...  # type: Tuple[Any, ...]
    keywords = ...  # type: Dict[str, Any]
    def __init__(self, func: Callable[..., _T], *args: Any, **kwargs: Any) -> None: ...
    def __call__(self, *args: Any, **kwargs: Any) -> _T: ...

if sys.version_info >= (3, 4):
    class _SingleDispatchCallable(Generic[_T]):
        registry = ...  # type: Mapping[Any, Callable[..., _T]]
        def dispatch(self, cls: Any) -> Callable[..., _T]: ...
        @overload
        def register(self, cls: Any) -> Callable[[Callable[..., _T]], Callable[..., _T]]: ...
        @overload
        def register(self, cls: Any, func: Callable[..., _T]) -> Callable[..., _T]: ...
        def _clear_cache(self) -> None: ...
        def __call__(self, *args: Any, **kwargs: Any) -> _T: ...

    def singledispatch(func: Callable[..., _T]) -> _SingleDispatchCallable[_T]: ...
