from typing import Any

from django.http.request import HttpRequest
from django.http.response import HttpResponse, HttpResponsePermanentRedirect
from django.utils.deprecation import MiddlewareMixin

class SecurityMiddleware(MiddlewareMixin):
    sts_seconds: int
    sts_include_subdomains: bool
    sts_preload: bool
    content_type_nosniff: bool
    xss_filter: bool
    redirect: bool
    redirect_host: str | None
    redirect_exempt: list[Any]
    def process_request(self, request: HttpRequest) -> HttpResponsePermanentRedirect | None: ...
    def process_response(self, request: HttpRequest, response: HttpResponse) -> HttpResponse: ...
