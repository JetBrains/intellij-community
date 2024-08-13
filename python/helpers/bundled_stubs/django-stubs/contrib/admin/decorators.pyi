from collections.abc import Callable, Sequence
from typing import Any, TypeVar, overload

from django.contrib.admin import ModelAdmin
from django.contrib.admin.sites import AdminSite
from django.db.models.base import Model
from django.db.models.expressions import BaseExpression, Combinable
from django.utils.functional import _StrOrPromise

_ModelAdmin = TypeVar("_ModelAdmin", bound=ModelAdmin[Any])
_F = TypeVar("_F", bound=Callable[..., Any])

@overload
def action(
    function: _F,
    permissions: Sequence[str] | None = ...,
    description: _StrOrPromise | None = ...,
) -> _F: ...
@overload
def action(
    *,
    permissions: Sequence[str] | None = ...,
    description: _StrOrPromise | None = ...,
) -> Callable[[_F], _F]: ...
@overload
def display(
    function: _F,
    boolean: bool | None = ...,
    ordering: str | Combinable | BaseExpression | None = ...,
    description: _StrOrPromise | None = ...,
    empty_value: str | None = ...,
) -> _F: ...
@overload
def display(
    *,
    boolean: bool | None = ...,
    ordering: str | Combinable | BaseExpression | None = ...,
    description: _StrOrPromise | None = ...,
    empty_value: str | None = ...,
) -> Callable[[_F], _F]: ...
def register(
    *models: type[Model], site: AdminSite | None = ...
) -> Callable[[type[_ModelAdmin]], type[_ModelAdmin]]: ...
