from collections import defaultdict
from collections.abc import Callable
from logging import Logger
from re import Pattern
from typing import Any

from django.http.request import HttpRequest
from django.http.response import HttpResponseBase, HttpResponseForbidden
from django.utils.deprecation import MiddlewareMixin
from django.utils.functional import cached_property

logger: Logger

invalid_token_chars_re: Pattern[str]

REASON_BAD_ORIGIN: str
REASON_NO_REFERER: str
REASON_BAD_REFERER: str
REASON_NO_CSRF_COOKIE: str
REASON_CSRF_TOKEN_MISSING: str
REASON_MALFORMED_REFERER: str
REASON_INSECURE_REFERER: str

REASON_INCORRECT_LENGTH: str
REASON_INVALID_CHARACTERS: str

CSRF_SECRET_LENGTH: int
CSRF_TOKEN_LENGTH: Any
CSRF_ALLOWED_CHARS: Any
CSRF_SESSION_KEY: str

def get_token(request: HttpRequest) -> str: ...
def rotate_token(request: HttpRequest) -> None: ...

class InvalidTokenFormat(Exception):
    reason: str
    def __init__(self, reason: str) -> None: ...

class RejectRequest(Exception):
    reason: str
    def __init__(self, reason: str) -> None: ...

class CsrfViewMiddleware(MiddlewareMixin):
    @cached_property
    def csrf_trusted_origins_hosts(self) -> list[str]: ...
    @cached_property
    def allowed_origins_exact(self) -> set[str]: ...
    @cached_property
    def allowed_origin_subdomains(self) -> defaultdict[str, list[str]]: ...
    def process_request(self, request: HttpRequest) -> None: ...
    def process_view(
        self, request: HttpRequest, callback: Callable | None, callback_args: tuple, callback_kwargs: dict[str, Any]
    ) -> HttpResponseForbidden | None: ...
    def process_response(self, request: HttpRequest, response: HttpResponseBase) -> HttpResponseBase: ...

def _get_new_csrf_string() -> str: ...
def _get_new_csrf_token() -> str: ...
