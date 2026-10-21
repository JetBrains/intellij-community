from collections.abc import Callable
from typing import Any

from django.middleware.csrf import CsrfViewMiddleware
from typing_extensions import TypeVar

csrf_protect: Callable[[_F], _F]

class _EnsureCsrfToken(CsrfViewMiddleware): ...

requires_csrf_token: Callable[[_F], _F]

class _EnsureCsrfCookie(CsrfViewMiddleware): ...

ensure_csrf_cookie: Callable[[_F], _F]

_F = TypeVar("_F", bound=Callable[..., Any])

def csrf_exempt(view_func: _F) -> _F: ...
