# Stubs for django.conf.urls (Python 3.5)
from collections.abc import Callable, Sequence
from typing import Any, overload

from django.http.response import HttpResponse, HttpResponseBase
from django.urls import URLPattern, URLResolver
from django.urls import include as include
from typing_extensions import TypeAlias

handler400: str | Callable[..., HttpResponse]
handler403: str | Callable[..., HttpResponse]
handler404: str | Callable[..., HttpResponse]
handler500: str | Callable[..., HttpResponse]

_IncludedURLConf: TypeAlias = tuple[Sequence[URLResolver | URLPattern], str | None, str | None]

# Deprecated
@overload
def url(
    regex: str, view: Callable[..., HttpResponseBase], kwargs: dict[str, Any] | None = ..., name: str | None = ...
) -> URLPattern: ...
@overload
def url(
    regex: str, view: _IncludedURLConf, kwargs: dict[str, Any] | None = ..., name: str | None = ...
) -> URLResolver: ...
@overload
def url(
    regex: str,
    view: Sequence[URLResolver | str],
    kwargs: dict[str, Any] | None = ...,
    name: str | None = ...,
) -> URLResolver: ...
