from collections.abc import Callable
from typing import overload

from django.utils.functional import _StrOrPromise
from typing_extensions import TypeVar

_C = TypeVar("_C", bound=Callable)

@overload
def staff_member_required(
    view_func: _C = ..., redirect_field_name: str | None = ..., login_url: _StrOrPromise = ...
) -> _C: ...
@overload
def staff_member_required(
    view_func: None = None, redirect_field_name: str | None = ..., login_url: _StrOrPromise = ...
) -> Callable: ...
