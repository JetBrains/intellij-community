from typing import Any, Callable, ContextManager, MutableMapping, Optional, TypeVar

_KT = TypeVar("_KT")
_T = TypeVar("_T", bound=Callable[..., Any])

def cached(
    cache: Optional[MutableMapping[_KT, Any]], key: Callable[..., _KT] = ..., lock: Optional[ContextManager[Any]] = ...
) -> Callable[[_T], _T]: ...
def cachedmethod(
    cache: Callable[[Any], Optional[MutableMapping[_KT, Any]]],
    key: Callable[..., _KT] = ...,
    lock: Optional[ContextManager[Any]] = ...,
) -> Callable[[_T], _T]: ...
