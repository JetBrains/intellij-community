from collections.abc import Callable, Mapping, Sequence
from typing import Any, Literal, Protocol, TypeVar, overload, type_check_only

from django.db.models import Manager, QuerySet
from django.db.models.base import Model
from django.http import HttpRequest
from django.http.response import HttpResponse, HttpResponsePermanentRedirect, HttpResponseRedirect

def render(
    request: HttpRequest | None,
    template_name: str | Sequence[str],
    context: Mapping[str, Any] | None = ...,
    content_type: str | None = ...,
    status: int | None = ...,
    using: str | None = ...,
) -> HttpResponse: ...
@type_check_only
class SupportsGetAbsoluteUrl(Protocol):
    def get_absolute_url(self) -> str: ...

@overload
def redirect(
    to: Callable[..., Any] | str | SupportsGetAbsoluteUrl, *args: Any, permanent: Literal[True], **kwargs: Any
) -> HttpResponsePermanentRedirect: ...
@overload
def redirect(
    to: Callable[..., Any] | str | SupportsGetAbsoluteUrl, *args: Any, permanent: Literal[False] = ..., **kwargs: Any
) -> HttpResponseRedirect: ...
@overload
def redirect(
    to: Callable[..., Any] | str | SupportsGetAbsoluteUrl, *args: Any, permanent: bool, **kwargs: Any
) -> HttpResponseRedirect | HttpResponsePermanentRedirect: ...

_T = TypeVar("_T", bound=Model)

def get_object_or_404(klass: type[_T] | Manager[_T] | QuerySet[_T], *args: Any, **kwargs: Any) -> _T: ...
async def aget_object_or_404(klass: type[_T] | Manager[_T] | QuerySet[_T], *args: Any, **kwargs: Any) -> _T: ...
def get_list_or_404(klass: type[_T] | Manager[_T] | QuerySet[_T], *args: Any, **kwargs: Any) -> list[_T]: ...
async def aget_list_or_404(klass: type[_T] | Manager[_T] | QuerySet[_T], *args: Any, **kwargs: Any) -> list[_T]: ...
def resolve_url(to: Callable | Model | str, *args: Any, **kwargs: Any) -> str: ...
