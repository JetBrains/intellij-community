from collections.abc import Awaitable, Callable
from typing import Any, ClassVar, Literal

from django.contrib.auth.models import _AnyUser
from django.http import HttpRequest, HttpResponseBase, HttpResponseRedirect
from django.utils.deprecation import MiddlewareMixin, _AsyncGetResponseCallable, _GetResponseCallable

def get_user(request: HttpRequest) -> _AnyUser: ...
async def auser(request: HttpRequest) -> _AnyUser: ...

class AuthenticationMiddleware(MiddlewareMixin):
    def process_request(self, request: HttpRequest) -> None: ...

class LoginRequiredMiddleware(MiddlewareMixin):
    redirect_field_name: ClassVar[str]

    def process_view(
        self,
        request: HttpRequest,
        view_func: Callable[..., HttpResponseBase],
        view_args: tuple[Any, ...],
        view_kwargs: dict[Any, Any],
    ) -> HttpResponseBase | None: ...
    def get_login_url(self, view_func: Callable[..., HttpResponseBase]) -> str: ...
    def get_redirect_field_name(self, view_func: Callable[..., HttpResponseBase]) -> str: ...
    def handle_no_permission(
        self, request: HttpRequest, view_func: Callable[..., HttpResponseBase]
    ) -> HttpResponseRedirect: ...

class RemoteUserMiddleware:
    header: ClassVar[str]
    force_logout_if_no_header: ClassVar[bool]
    sync_capable: ClassVar[Literal[True]]
    async_capable: ClassVar[Literal[True]]
    get_response: _GetResponseCallable | _AsyncGetResponseCallable
    is_async: bool
    def __init__(self, get_response: _GetResponseCallable | _AsyncGetResponseCallable) -> None: ...
    def __call__(self, request: HttpRequest) -> HttpResponseBase | Awaitable[HttpResponseBase]: ...
    async def __acall__(self, request: HttpRequest) -> HttpResponseBase: ...
    def clean_username(self, username: str, request: HttpRequest) -> str: ...
    def process_request(self, request: HttpRequest) -> None: ...
    async def aprocess_request(self, request: HttpRequest) -> None: ...

class PersistentRemoteUserMiddleware(RemoteUserMiddleware):
    force_logout_if_no_header: ClassVar[bool]
