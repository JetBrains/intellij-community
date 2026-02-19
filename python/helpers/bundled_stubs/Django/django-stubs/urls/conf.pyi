from collections.abc import Callable, Coroutine, Sequence
from types import ModuleType
from typing import Any, TypeAlias, overload

from django.urls import URLPattern, URLResolver, _AnyURL
from django.utils.functional import _StrOrPromise

from ..http.response import HttpResponseBase

_URLConf: TypeAlias = str | ModuleType | Sequence[_AnyURL]
_IncludedURLConf: TypeAlias = tuple[Sequence[URLResolver | URLPattern], str | None, str | None]

def include(arg: _URLConf | tuple[_URLConf, str], namespace: str | None = ...) -> _IncludedURLConf: ...

# path()
@overload
def path(
    route: _StrOrPromise, view: Callable[..., HttpResponseBase], kwargs: dict[str, Any] = ..., name: str = ...
) -> URLPattern: ...
@overload
def path(
    route: _StrOrPromise,
    view: Callable[..., Coroutine[Any, Any, HttpResponseBase]],
    kwargs: dict[str, Any] = ...,
    name: str = ...,
) -> URLPattern: ...
@overload
def path(
    route: _StrOrPromise, view: _IncludedURLConf, kwargs: dict[str, Any] = ..., name: str = ...
) -> URLResolver: ...
@overload
def path(
    route: _StrOrPromise, view: Sequence[URLResolver | str], kwargs: dict[str, Any] = ..., name: str = ...
) -> URLResolver: ...

# re_path()
@overload
def re_path(
    route: _StrOrPromise, view: Callable[..., HttpResponseBase], kwargs: dict[str, Any] = ..., name: str = ...
) -> URLPattern: ...
@overload
def re_path(
    route: _StrOrPromise,
    view: Callable[..., Coroutine[Any, Any, HttpResponseBase]],
    kwargs: dict[str, Any] = ...,
    name: str = ...,
) -> URLPattern: ...
@overload
def re_path(
    route: _StrOrPromise, view: _IncludedURLConf, kwargs: dict[str, Any] = ..., name: str = ...
) -> URLResolver: ...
@overload
def re_path(
    route: _StrOrPromise, view: Sequence[URLResolver | str], kwargs: dict[str, Any] = ..., name: str = ...
) -> URLResolver: ...
