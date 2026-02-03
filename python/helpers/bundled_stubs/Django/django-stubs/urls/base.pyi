from collections.abc import Callable, Mapping, Sequence
from typing import Any, Literal, TypeAlias

from django.http.request import QueryDict
from django.http.response import HttpResponseBase
from django.urls.resolvers import ResolverMatch
from django.utils.functional import _StrPromise

# https://github.com/python/typeshed/blob/87f599dc8312ac67b941b5f2b47274534a1a2d3a/stdlib/urllib/parse.pyi#L136-L138
_QueryType: TypeAlias = (
    Mapping[Any, Any] | Mapping[Any, Sequence[Any]] | Sequence[tuple[Any, Any]] | Sequence[tuple[Any, Sequence[Any]]]
)

def resolve(path: str | _StrPromise, urlconf: str | None = ...) -> ResolverMatch: ...

# NOTE: make sure `reverse` and `reverse_lazy` objects are in sync:
def reverse(
    viewname: Callable[..., HttpResponseBase] | str | None,
    urlconf: str | None = None,
    args: Sequence[Any] | None = None,
    kwargs: dict[str, Any] | None = None,
    current_app: str | None = None,
    *,
    query: QueryDict | _QueryType | None = None,
    fragment: str | None = None,
) -> str: ...
def reverse_lazy(
    viewname: Callable[..., HttpResponseBase] | str | None,
    urlconf: str | None = None,
    args: Sequence[Any] | None = None,
    kwargs: dict[str, Any] | None = None,
    current_app: str | None = None,
    *,
    query: QueryDict | _QueryType | None = None,
    fragment: str | None = None,
) -> _StrPromise: ...
def clear_url_caches() -> None: ...
def set_script_prefix(prefix: str) -> None: ...
def get_script_prefix() -> str: ...
def clear_script_prefix() -> None: ...
def set_urlconf(urlconf_name: str | None) -> None: ...
def get_urlconf(default: str | None = ...) -> str | None: ...
def is_valid_path(path: str, urlconf: str | None = ...) -> Literal[False] | ResolverMatch: ...
def translate_url(url: str, lang_code: str) -> str: ...
