from collections.abc import Callable, Iterable
from typing import Any, Protocol, TypeAlias, TypeVar, type_check_only

from django.http.response import HttpResponseBase
from django.utils.deprecation import MiddlewareMixin
from django.views.generic.base import View

@type_check_only
class _MiddlewareClass(Protocol):
    def __call__(self, get_response: Callable[..., Any], *args: Any, **kwargs: Any) -> Any: ...

_ViewType = TypeVar("_ViewType", bound=View | Callable[..., Any])  # Any callable
_CallableType = TypeVar("_CallableType", bound=Callable[..., Any])
_ViewDecorator: TypeAlias = Callable[
    [Callable[..., HttpResponseBase]],
    Callable[..., HttpResponseBase],
]
_DecoratorFactory: TypeAlias = Callable[..., _ViewDecorator]

classonlymethod = classmethod

def method_decorator(
    decorator: _ViewDecorator | _DecoratorFactory | Iterable[_ViewDecorator | _DecoratorFactory],
    name: str = "",
) -> Callable[[_ViewType], _ViewType]: ...
def decorator_from_middleware_with_args(middleware_class: _MiddlewareClass) -> _DecoratorFactory: ...
def decorator_from_middleware(middleware_class: _MiddlewareClass) -> _ViewDecorator: ...
def make_middleware_decorator(middleware_class: type[MiddlewareMixin]) -> _DecoratorFactory: ...
def sync_and_async_middleware(func: _CallableType) -> _CallableType: ...
def sync_only_middleware(func: _CallableType) -> _CallableType: ...
def async_only_middleware(func: _CallableType) -> _CallableType: ...
