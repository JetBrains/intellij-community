import sys
from io import BytesIO, IOBase
from typing import Any

from django.core.handlers import base
from django.http import HttpRequest
from django.http.response import HttpResponseBase

if sys.version_info >= (3, 11):
    from wsgiref.types import StartResponse, WSGIEnvironment
else:
    from _typeshed.wsgi import StartResponse, WSGIEnvironment

class LimitedStream(IOBase):
    limit: int
    def __init__(self, stream: BytesIO, limit: int) -> None: ...
    def read(self, size: int | None = ...) -> bytes: ...
    def readline(self, size: int | None = ...) -> bytes: ...

class WSGIRequest(HttpRequest):
    environ: WSGIEnvironment
    def __init__(self, environ: WSGIEnvironment) -> None: ...

class WSGIHandler(base.BaseHandler):
    request_class: type[WSGIRequest]
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def __call__(
        self,
        environ: WSGIEnvironment,
        start_response: StartResponse,
    ) -> HttpResponseBase: ...

def get_path_info(environ: WSGIEnvironment) -> str: ...
def get_script_name(environ: WSGIEnvironment) -> str: ...
def get_bytes_from_wsgi(environ: WSGIEnvironment, key: str, default: str) -> bytes: ...
def get_str_from_wsgi(environ: WSGIEnvironment, key: str, default: str) -> str: ...
