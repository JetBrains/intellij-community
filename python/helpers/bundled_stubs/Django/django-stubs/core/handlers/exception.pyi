from collections.abc import Awaitable, Callable
from typing import Any

from django.http.request import HttpRequest
from django.http.response import HttpResponse, HttpResponseBase
from django.urls.resolvers import URLResolver

def convert_exception_to_response(
    get_response: Callable[[HttpRequest], HttpResponseBase | Awaitable[HttpResponseBase]]
) -> Callable[[HttpRequest], HttpResponseBase | Awaitable[HttpResponseBase]]: ...
def response_for_exception(request: HttpRequest, exc: Exception) -> HttpResponse: ...
def get_exception_response(
    request: HttpRequest, resolver: URLResolver, status_code: int, exception: Exception
) -> HttpResponse: ...
def handle_uncaught_exception(request: HttpRequest, resolver: URLResolver, exc_info: Any) -> HttpResponse: ...
