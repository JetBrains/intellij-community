from collections.abc import Callable, Iterator, Sequence
from re import Pattern
from types import ModuleType
from typing import Any, overload

from django.core.checks.messages import CheckMessage
from django.http import HttpRequest, HttpResponse
from django.urls import _AnyURL
from django.utils.datastructures import MultiValueDict
from django.utils.functional import cached_property
from typing_extensions import TypeAlias

class ResolverMatch:
    func: Callable
    args: tuple[Any, ...]
    kwargs: dict[str, Any]
    url_name: str | None
    app_names: list[str]
    app_name: str
    namespaces: list[str]
    namespace: str
    view_name: str
    route: str
    tried: Any | None
    _func_path: str
    def __init__(
        self,
        func: Callable,
        args: tuple[Any, ...],
        kwargs: dict[str, Any],
        url_name: str | None = ...,
        app_names: list[str | None] | None = ...,
        namespaces: list[str | None] | None = ...,
        route: str | None = ...,
        tried: Any | None = ...,
        captured_kwargs: dict[str, Any] | None = ...,
        extra_kwargs: dict[str, Any] | None = ...,
    ) -> None: ...
    def __getitem__(self, index: int) -> Any: ...
    # for tuple unpacking
    def __iter__(self) -> Iterator[Any]: ...

def get_resolver(urlconf: str | None = ...) -> URLResolver: ...
def get_ns_resolver(ns_pattern: str, resolver: URLResolver, converters: tuple) -> URLResolver: ...

_Pattern: TypeAlias = RegexPattern | RoutePattern | LocalePrefixPattern

class LocaleRegexDescriptor:
    attr: str
    def __init__(self, attr: Any) -> None: ...
    @overload
    def __get__(self, instance: None, cls: type[_Pattern] = ...) -> LocaleRegexDescriptor: ...
    @overload
    def __get__(self, instance: _Pattern, cls: type[_Pattern] = ...) -> Pattern[str]: ...

class CheckURLMixin:
    def describe(self) -> str: ...

class RegexPattern(CheckURLMixin):
    regex: LocaleRegexDescriptor
    name: str | None
    converters: dict[str, Any]
    def __init__(self, regex: str, name: str | None = ..., is_endpoint: bool = ...) -> None: ...
    def match(self, path: str) -> tuple[str, tuple, dict[str, str]] | None: ...
    def check(self) -> list[CheckMessage]: ...

class RoutePattern(CheckURLMixin):
    regex: LocaleRegexDescriptor
    name: str | None
    converters: dict[str, Any]
    def __init__(self, route: str, name: str | None = ..., is_endpoint: bool = ...) -> None: ...
    def match(self, path: str) -> tuple[str, tuple, dict[str, int | str]] | None: ...
    def check(self) -> list[CheckMessage]: ...

class LocalePrefixPattern:
    prefix_default_language: bool
    converters: dict[str, Any]
    def __init__(self, prefix_default_language: bool = ...) -> None: ...
    @property
    def regex(self) -> Pattern[str]: ...
    @property
    def language_prefix(self) -> str: ...
    def match(self, path: str) -> tuple[str, tuple, dict[str, Any]] | None: ...
    def check(self) -> list[CheckMessage]: ...
    def describe(self) -> str: ...

class URLPattern:
    pattern: _Pattern
    callback: Callable
    default_args: dict[str, Any]
    name: str | None
    def __init__(
        self,
        pattern: _Pattern,
        callback: Callable,
        default_args: dict[str, Any] | None = ...,
        name: str | None = ...,
    ) -> None: ...
    def check(self) -> list[CheckMessage]: ...
    def resolve(self, path: str) -> ResolverMatch | None: ...
    @cached_property
    def lookup_str(self) -> str: ...

class URLResolver:
    pattern: _Pattern
    urlconf_name: str | Sequence[_AnyURL] | ModuleType
    callback: None
    default_kwargs: dict[str, Any]
    namespace: str | None
    app_name: str | None
    _local: Any
    _reverse_dict: MultiValueDict
    def __init__(
        self,
        pattern: _Pattern,
        urlconf_name: str | Sequence[_AnyURL] | ModuleType,
        default_kwargs: dict[str, Any] | None = ...,
        app_name: str | None = ...,
        namespace: str | None = ...,
    ) -> None: ...
    @property
    def reverse_dict(self) -> MultiValueDict: ...
    @property
    def namespace_dict(self) -> dict[str, tuple[str, URLResolver]]: ...
    @property
    def app_dict(self) -> dict[str, list[str]]: ...
    @cached_property
    def urlconf_module(self) -> ModuleType | None | Sequence[_AnyURL]: ...
    @cached_property
    def url_patterns(self) -> list[_AnyURL]: ...
    def resolve(self, path: str) -> ResolverMatch: ...
    def resolve_error_handler(self, view_type: int) -> Callable[[HttpRequest], HttpResponse]: ...
    def reverse(self, lookup_view: str, *args: Any, **kwargs: Any) -> str: ...
    def _is_callback(self, name: str) -> bool: ...
    def _populate(self) -> None: ...
    def check(self) -> list[CheckMessage]: ...
