import sys
from collections.abc import Awaitable, Callable, Mapping
from typing import Any
from urllib.parse import ParseResult

from django.core.handlers.asgi import ASGIHandler
from django.core.handlers.base import BaseHandler
from django.core.handlers.wsgi import WSGIHandler
from django.http import HttpRequest
from django.http.response import FileResponse, HttpResponseBase

if sys.version_info >= (3, 11):
    from wsgiref.types import StartResponse, WSGIEnvironment
else:
    from _typeshed.wsgi import StartResponse, WSGIEnvironment

class StaticFilesHandlerMixin:
    handles_files: bool
    application: BaseHandler
    base_url: ParseResult
    def load_middleware(self) -> None: ...
    def get_base_url(self) -> str: ...
    def _should_handle(self, path: str) -> bool: ...
    def file_path(self, url: str) -> str: ...
    def serve(self, request: HttpRequest) -> FileResponse: ...
    def get_response(self, request: HttpRequest) -> HttpResponseBase: ...
    async def get_response_async(self, request: HttpRequest) -> HttpResponseBase: ...

class StaticFilesHandler(StaticFilesHandlerMixin, WSGIHandler):  # type: ignore[misc]
    application: WSGIHandler
    base_url: ParseResult
    def __init__(self, application: WSGIHandler) -> None: ...
    def __call__(
        self,
        environ: WSGIEnvironment,
        start_response: StartResponse,
    ) -> HttpResponseBase: ...

class ASGIStaticFilesHandler(StaticFilesHandlerMixin, ASGIHandler):  # type: ignore[misc]
    application: ASGIHandler
    base_url: ParseResult
    def __init__(self, application: ASGIHandler) -> None: ...
    async def __call__(
        self,
        scope: dict[str, Any],
        receive: Callable[[], Awaitable[Mapping[str, Any]]],
        send: Callable[[Mapping[str, Any]], Awaitable[None]],
    ) -> None: ...
