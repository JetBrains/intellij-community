from collections.abc import Callable, Iterable
from typing import TypeVar, overload

from django.contrib.auth.base_user import _UserModel
from django.contrib.auth.models import AnonymousUser
from django.http.response import HttpResponseBase

_VIEW = TypeVar("_VIEW", bound=Callable[..., HttpResponseBase])

def user_passes_test(
    test_func: Callable[[_UserModel | AnonymousUser], bool],
    login_url: str | None = ...,
    redirect_field_name: str | None = ...,
) -> Callable[[_VIEW], _VIEW]: ...

# There are two ways of calling @login_required: @with(arguments) and @bare
@overload
def login_required(redirect_field_name: str | None = ..., login_url: str | None = ...) -> Callable[[_VIEW], _VIEW]: ...
@overload
def login_required(function: _VIEW, redirect_field_name: str | None = ..., login_url: str | None = ...) -> _VIEW: ...
def login_not_required(view_func: _VIEW) -> _VIEW: ...
def permission_required(
    perm: Iterable[str] | str, login_url: str | None = ..., raise_exception: bool = ...
) -> Callable[[_VIEW], _VIEW]: ...
