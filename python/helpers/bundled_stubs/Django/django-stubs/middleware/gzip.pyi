from typing import Any, ClassVar

from django.http.request import HttpRequest
from django.http.response import HttpResponseBase
from django.utils.deprecation import MiddlewareMixin

re_accepts_gzip: Any

class GZipMiddleware(MiddlewareMixin):
    max_random_bytes: ClassVar[int]
    def process_response(self, request: HttpRequest, response: HttpResponseBase) -> HttpResponseBase: ...
